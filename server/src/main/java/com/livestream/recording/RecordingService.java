package com.livestream.recording;

import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastRepository;
import com.livestream.broadcast.BroadcastService;
import com.livestream.stream.MediaMtxClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordingService {
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    /** mediamtx.yml recordPath의 파일 이름 부분 %Y-%m-%d_%H-%M-%S-%f%z → 예: 2026-11-13_10-53-11-123456Z.mp4 */
    private static final Pattern FILE_TIME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{6}(?:Z|[+-]\\d{4}))\\.mp4$");
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSXX");
    private static final Pattern LIVE_PATH = Pattern.compile("^live/([A-Za-z0-9_-]+)$");

    private final RecordingRepository recordings;
    private final BroadcastRepository broadcastRepo;
    private final BroadcastService broadcasts;
    private final MediaMtxClient mediamtx;
    private final Clock clock;

    public RecordingService(RecordingRepository recordings, BroadcastRepository broadcastRepo, BroadcastService broadcasts,
                            MediaMtxClient mediamtx, Clock clock) {
        this.recordings = recordings;
        this.broadcastRepo = broadcastRepo;
        this.broadcasts = broadcasts;
        this.mediamtx = mediamtx;
        this.clock = clock;
    }

    /** 파일 이름에서 녹화 시작 시각을 읽는다. 형식이 다르면 empty. */
    static Optional<Instant> startOf(String segmentPath) {
        Matcher m = FILE_TIME.matcher(Path.of(segmentPath).getFileName().toString());
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(m.group(1), FILE_TIME_FORMAT).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * MediaMTX 녹화 완료 알림(record-hook.sh) → 위치 저장. 등록된 방송의 경로만 저장하고(개발용 키는 무시),
     * 같은 파일이 두 번 와도 한 번만 저장한다(멱등).
     */
    @Transactional
    public Optional<Recording> register(String streamPath, String segmentPath, double durationSeconds) {
        Matcher key = LIVE_PATH.matcher(streamPath == null ? "" : streamPath);
        if (!key.matches() || segmentPath == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path는 live/<스트림 키>, segmentPath 필요");
        }
        Optional<Broadcast> b = broadcastRepo.findByStreamKey(key.group(1));
        if (b.isEmpty()) return Optional.empty();
        Optional<Recording> existing = recordings.findByFilePath(segmentPath);
        if (existing.isPresent()) return existing;
        long durationMs = Math.round(durationSeconds * 1000);
        Instant start = startOf(segmentPath).orElseGet(() -> clock.instant().minusMillis(durationMs));
        Recording r = recordings.save(new Recording(b.get().getId(), streamPath, segmentPath, start, durationMs, clock.instant()));
        log.info("녹화 저장: 방송 #{} {} ({}초)", b.get().getId(), segmentPath, durationSeconds);
        return Optional.of(r);
    }

    @Transactional(readOnly = true)
    public List<Recording> list() {
        return recordings.findTop200ByOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Recording> listOf(Long broadcastId) {
        broadcasts.get(broadcastId);
        return recordings.findAllByBroadcastIdOrderByStartedAtAsc(broadcastId);
    }

    @Transactional(readOnly = true)
    public Recording get(Long id) {
        return recordings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "녹화 없음"));
    }

    /** 방송 소유자만. 파일(MediaMTX)과 DB 행을 함께 지운다. */
    @Transactional
    public void delete(Long userId, Long id) {
        Recording r = get(id);
        Broadcast b = broadcasts.get(r.getBroadcastId());
        if (!b.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 소유자만 녹화를 삭제할 수 있음");
        }
        try {
            mediamtx.deleteRecordingSegment(r.getStreamPath(), r.getStartedAt());
        } catch (RuntimeException e) {
            // 파일이 이미 없거나 MediaMTX가 꺼져 있어도 목록에서는 지운다 (파일은 recordings 볼륨에서 수동 정리)
            log.warn("녹화 파일 삭제 실패 {}: {}", r.getFilePath(), e.getMessage());
        }
        recordings.delete(r);
    }
}
