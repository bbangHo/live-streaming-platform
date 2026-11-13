package com.livestream.recording;

import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastRepository;
import com.livestream.user.User;
import com.livestream.user.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 녹화 목록·상세는 공개(SecurityConfig), 삭제는 방송 소유자(RecordingService.delete). */
@RestController
public class RecordingController {
    private final RecordingService service;
    private final BroadcastRepository broadcasts;
    private final UserRepository users;

    public RecordingController(RecordingService service, BroadcastRepository broadcasts, UserRepository users) {
        this.service = service;
        this.broadcasts = broadcasts;
        this.users = users;
    }

    /**
     * playbackPath: 같은 출처 기준 재생 주소. nginx·Caddy가 /playback/ 을 MediaMTX playback 서버로 넘기고,
     * 그 서버가 녹화 파일을 mp4 한 파일로 내려준다(탐색 가능).
     */
    public record RecordingResponse(Long id, Long broadcastId, String broadcastTitle, Long ownerId, String ownerUsername,
                                    String filePath, Instant startedAt, long durationMs, String playbackPath) {}

    @GetMapping("/api/recordings")
    public List<RecordingResponse> list() {
        return toResponses(service.list());
    }

    @GetMapping("/api/broadcasts/{broadcastId}/recordings")
    public List<RecordingResponse> listOf(@PathVariable Long broadcastId) {
        return toResponses(service.listOf(broadcastId));
    }

    @GetMapping("/api/recordings/{id}")
    public RecordingResponse get(@PathVariable Long id) {
        return toResponses(List.of(service.get(id))).getFirst();
    }

    @DeleteMapping("/api/recordings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    static String playbackPath(Recording r) {
        // 길이를 조금 넉넉히: 끝부분이 잘리지 않게 (MediaMTX가 실제 파일 끝에서 멈춘다)
        double seconds = Math.ceil(r.getDurationMs() / 1000.0) + 1;
        return "/playback/get?path=" + URLEncoder.encode(r.getStreamPath(), StandardCharsets.UTF_8)
                + "&start=" + URLEncoder.encode(r.getStartedAt().toString(), StandardCharsets.UTF_8)
                + "&duration=" + (long) seconds + "&format=mp4";
    }

    private List<RecordingResponse> toResponses(List<Recording> list) {
        Map<Long, Broadcast> bs = broadcasts.findAllById(list.stream().map(Recording::getBroadcastId).distinct().toList())
                .stream().collect(Collectors.toMap(Broadcast::getId, Function.identity()));
        Map<Long, String> names = users.findAllById(bs.values().stream().map(Broadcast::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return list.stream().map(r -> {
            Broadcast b = bs.get(r.getBroadcastId());
            return new RecordingResponse(r.getId(), r.getBroadcastId(), b == null ? null : b.getTitle(),
                    b == null ? null : b.getOwnerId(), b == null ? null : names.get(b.getOwnerId()),
                    r.getFilePath(), r.getStartedAt(), r.getDurationMs(), playbackPath(r));
        }).toList();
    }
}
