package com.livestream.broadcast;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** GET은 공개(SecurityConfig), 나머지는 로그인 + 소유자(BroadcastService.requireOwner). */
@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {
    private final BroadcastService service;

    public BroadcastController(BroadcastService service) {
        this.service = service;
    }

    public record TitleRequest(@NotBlank @Size(max = 100) String title) {}

    public record BroadcastResponse(Long id, Long ownerId, String title, Broadcast.Status status, Instant createdAt) {
        static BroadcastResponse of(Broadcast b) {
            return new BroadcastResponse(b.getId(), b.getOwnerId(), b.getTitle(), b.getStatus(), b.getCreatedAt());
        }
    }

    @GetMapping
    public List<BroadcastResponse> list() {
        return service.list().stream().map(BroadcastResponse::of).toList();
    }

    @GetMapping("/{id}")
    public BroadcastResponse get(@PathVariable Long id) {
        return BroadcastResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BroadcastResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody TitleRequest req) {
        return BroadcastResponse.of(service.create(userId, req.title()));
    }

    @PatchMapping("/{id}")
    public BroadcastResponse rename(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                    @Valid @RequestBody TitleRequest req) {
        return BroadcastResponse.of(service.rename(userId, id, req.title()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/start")
    public BroadcastResponse start(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return BroadcastResponse.of(service.changeStatus(userId, id, Broadcast.Status.LIVE));
    }

    @PostMapping("/{id}/stop")
    public BroadcastResponse stop(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return BroadcastResponse.of(service.changeStatus(userId, id, Broadcast.Status.ENDED));
    }
}
