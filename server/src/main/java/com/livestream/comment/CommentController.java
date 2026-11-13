package com.livestream.comment;

import com.livestream.user.User;
import com.livestream.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 조회는 공개(SecurityConfig), 작성은 로그인, 수정·삭제는 작성자(CommentService.requireAuthor). */
@RestController
public class CommentController {
    private final CommentService service;
    private final UserRepository users;

    public CommentController(CommentService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    public record CommentRequest(@NotBlank @Size(max = 500) String content) {}

    public record CommentResponse(Long id, Long broadcastId, Long authorId, String authorUsername, String content,
                                  Instant createdAt, Instant updatedAt) {
        static CommentResponse of(Comment c, String authorUsername) {
            return new CommentResponse(c.getId(), c.getBroadcastId(), c.getAuthorId(), authorUsername, c.getContent(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    /** after: 이 id 이후 댓글만 (화면이 2~3초마다 새 댓글만 받아 간다). 최대 200개. */
    @GetMapping("/api/broadcasts/{broadcastId}/comments")
    public List<CommentResponse> list(@PathVariable Long broadcastId, @RequestParam(defaultValue = "0") long after) {
        List<Comment> list = service.list(broadcastId, after);
        Map<Long, String> names = users.findAllById(list.stream().map(Comment::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return list.stream().map(c -> CommentResponse.of(c, names.get(c.getAuthorId()))).toList();
    }

    @PostMapping("/api/broadcasts/{broadcastId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse write(@AuthenticationPrincipal Long userId, @PathVariable Long broadcastId,
                                 @Valid @RequestBody CommentRequest req) {
        return toResponse(service.write(userId, broadcastId, req.content()));
    }

    @PatchMapping("/api/comments/{id}")
    public CommentResponse edit(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                @Valid @RequestBody CommentRequest req) {
        return toResponse(service.edit(userId, id, req.content()));
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    private CommentResponse toResponse(Comment c) {
        return CommentResponse.of(c, users.findById(c.getAuthorId()).map(User::getUsername).orElse(null));
    }
}
