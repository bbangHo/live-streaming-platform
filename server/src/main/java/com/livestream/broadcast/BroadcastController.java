package com.livestream.broadcast;

import com.livestream.user.User;
import com.livestream.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** GET은 공개(SecurityConfig), 나머지는 로그인 + 소유자(BroadcastService.requireOwner). */
@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {
    private final BroadcastService service;
    private final UserRepository users;

    public BroadcastController(BroadcastService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    public record BroadcastRequest(@NotBlank @Size(max = 100) String title, @Size(max = 1000) String description) {}

    /** hlsPath: 같은 출처 기준 시청 주소(nginx·Caddy가 /live/ 를 MediaMTX로 넘긴다). */
    public record BroadcastResponse(Long id, Long ownerId, String ownerUsername, String title, String description,
                                    Broadcast.Status status, String streamKey, String hlsPath, Instant createdAt,
                                    Instant startedAt, Instant endedAt) {
        static BroadcastResponse of(Broadcast b, String ownerUsername) {
            return new BroadcastResponse(b.getId(), b.getOwnerId(), ownerUsername, b.getTitle(), b.getDescription(),
                    b.getStatus(), b.getStreamKey(), "/live/" + b.getStreamKey() + "/index.m3u8", b.getCreatedAt(),
                    b.getStartedAt(), b.getEndedAt());
        }
    }

    @GetMapping
    public List<BroadcastResponse> list(@RequestParam(required = false) Broadcast.Status status) {
        List<Broadcast> list = service.list(status);
        Map<Long, String> names = users.findAllById(list.stream().map(Broadcast::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return list.stream().map(b -> BroadcastResponse.of(b, names.get(b.getOwnerId()))).toList();
    }

    @GetMapping("/{id}")
    public BroadcastResponse get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BroadcastResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody BroadcastRequest req) {
        return toResponse(service.create(userId, req.title(), req.description()));
    }

    @PatchMapping("/{id}")
    public BroadcastResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                    @Valid @RequestBody BroadcastRequest req) {
        return toResponse(service.update(userId, id, req.title(), req.description()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/stop")
    public BroadcastResponse stop(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return toResponse(service.stop(userId, id));
    }

    private BroadcastResponse toResponse(Broadcast b) {
        return BroadcastResponse.of(b, users.findById(b.getOwnerId()).map(User::getUsername).orElse(null));
    }
}
