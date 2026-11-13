package com.livestream.stream;

import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방송 상태를 실제 송출에 맞춘다. MediaMTX가 진실 공급원(source of truth).
 * - 대기 방송에 송출이 들어오면 → 방송 중
 * - 방송 중인데 송출이 없으면 → 유예(기본 30초) 안에 다시 붙으면 유지, 넘으면 종료 (새로고침·순간 끊김 대비)
 * - 종료된 방송(소유자 종료)이나 등록 안 된 키(devBypass 꺼짐)에 붙은 송출 → 끊는다
 * 웹훅 대신 주기 조회(2초)를 쓰는 이유: 백엔드나 MediaMTX가 재시작해 이벤트를 놓쳐도 다음 조회에서 맞춰진다.
 */
@Component
public class StreamStatusSync {
    private static final Logger log = LoggerFactory.getLogger(StreamStatusSync.class);

    private final MediaMtxClient mediamtx;
    private final BroadcastRepository broadcasts;
    private final MediaProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    /** 방송 id → 송출이 처음 끊긴 것을 본 시각. 백엔드가 재시작하면 비지만, 그때는 유예가 한 번 더 주어질 뿐이다. */
    private final Map<Long, Instant> offlineSince = new HashMap<>();

    public StreamStatusSync(MediaMtxClient mediamtx, BroadcastRepository broadcasts, MediaProperties props,
                            Clock clock, TransactionTemplate tx) {
        this.mediamtx = mediamtx;
        this.broadcasts = broadcasts;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.media.sync-interval:2s}")
    public void poll() {
        if (!props.syncEnabled()) return;
        List<MediaMtxClient.OnlinePath> online;
        try {
            online = mediamtx.onlinePaths();
        } catch (RuntimeException e) {
            // MediaMTX를 못 보면 아무것도 바꾸지 않는다 (전부 종료시키는 사고 방지)
            log.warn("MediaMTX 상태 조회 실패: {}", e.getMessage());
            return;
        }
        for (MediaMtxClient.OnlinePath p : reconcile(online, clock.instant())) {
            try {
                mediamtx.kick(p);
                log.info("송출 끊음: {}", p.name());
            } catch (RuntimeException e) {
                log.warn("송출 끊기 실패 {}: {}", p.name(), e.getMessage());
            }
        }
    }

    /** 상태를 맞추고, 끊어야 할 송출 목록을 돌려준다. 테스트에서 시각을 넣어 직접 부른다. */
    public synchronized List<MediaMtxClient.OnlinePath> reconcile(List<MediaMtxClient.OnlinePath> online, Instant now) {
        Map<String, MediaMtxClient.OnlinePath> byKey = new HashMap<>();
        for (MediaMtxClient.OnlinePath p : online) {
            StreamAuthService.streamKeyOf(p.name()).ifPresent(k -> byKey.put(k, p));
        }
        List<MediaMtxClient.OnlinePath> toKick = new ArrayList<>();
        tx.executeWithoutResult(status -> {
            Map<String, Broadcast> known = broadcasts.findAllByStreamKeyIn(byKey.keySet()).stream()
                    .collect(Collectors.toMap(Broadcast::getStreamKey, Function.identity()));

            for (var e : byKey.entrySet()) {
                Broadcast b = known.get(e.getKey());
                if (b == null) {
                    if (!props.devBypass()) toKick.add(e.getValue());
                } else if (b.getStatus() == Broadcast.Status.READY) {
                    b.goLive(now);
                    log.info("방송 시작: #{} {}", b.getId(), b.getTitle());
                } else if (b.getStatus() == Broadcast.Status.ENDED) {
                    toKick.add(e.getValue());
                }
            }

            Set<Long> liveIds = new HashSet<>();
            for (Broadcast b : broadcasts.findAllByStatus(Broadcast.Status.LIVE)) {
                liveIds.add(b.getId());
                if (byKey.containsKey(b.getStreamKey())) {
                    offlineSince.remove(b.getId());
                    continue;
                }
                Instant since = offlineSince.computeIfAbsent(b.getId(), id -> now);
                if (Duration.between(since, now).compareTo(props.grace()) >= 0) {
                    b.end(now);
                    offlineSince.remove(b.getId());
                    log.info("방송 종료(송출 끊김 {}초 초과): #{}", props.grace().toSeconds(), b.getId());
                }
            }
            offlineSince.keySet().retainAll(liveIds); // 소유자가 종료했거나 삭제된 방송은 잊는다
        });
        return toKick;
    }
}
