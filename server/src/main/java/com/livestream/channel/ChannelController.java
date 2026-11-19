package com.livestream.channel;

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

/** 채널 조회·게시글 목록은 공개(SecurityConfig), 팔로우·작성은 로그인, 게시글 삭제는 작성자·채널 주인(ChannelService). */
@RestController
public class ChannelController {
    private final ChannelService service;
    private final UserRepository users;

    public ChannelController(ChannelService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    public record PostRequest(@NotBlank @Size(max = 1000) String content) {}

    public record PostResponse(Long id, Long channelId, Long authorId, String authorUsername, String content,
                               Instant createdAt) {
        static PostResponse of(ChannelPost p, String authorUsername) {
            return new PostResponse(p.getId(), p.getChannelId(), p.getAuthorId(), authorUsername, p.getContent(),
                    p.getCreatedAt());
        }
    }

    @GetMapping("/api/channels/{userId}")
    public ChannelService.Channel get(@AuthenticationPrincipal Long viewerId, @PathVariable Long userId) {
        return service.get(userId, viewerId);
    }

    @PutMapping("/api/channels/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        service.follow(me, userId);
    }

    @DeleteMapping("/api/channels/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Long me, @PathVariable Long userId) {
        service.unfollow(me, userId);
    }

    @GetMapping("/api/me/following")
    public List<ChannelService.FollowedChannel> following(@AuthenticationPrincipal Long me) {
        return service.following(me);
    }

    @GetMapping("/api/channels/{userId}/posts")
    public List<PostResponse> posts(@PathVariable Long userId) {
        List<ChannelPost> list = service.posts(userId);
        Map<Long, String> names = users.findAllById(list.stream().map(ChannelPost::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return list.stream().map(p -> PostResponse.of(p, names.get(p.getAuthorId()))).toList();
    }

    @PostMapping("/api/channels/{userId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse write(@AuthenticationPrincipal Long me, @PathVariable Long userId,
                              @Valid @RequestBody PostRequest req) {
        ChannelPost p = service.write(me, userId, req.content());
        return PostResponse.of(p, users.findById(me).map(User::getUsername).orElse(null));
    }

    @DeleteMapping("/api/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        service.deletePost(me, id);
    }
}
