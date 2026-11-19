package com.livestream.channel;

import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastRepository;
import com.livestream.user.User;
import com.livestream.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 채널 = 사용자. 팔로우와 채널 커뮤니티 게시글. */
@Service
public class ChannelService {
    private final UserRepository users;
    private final BroadcastRepository broadcasts;
    private final FollowRepository follows;
    private final ChannelPostRepository posts;
    private final Clock clock;

    public ChannelService(UserRepository users, BroadcastRepository broadcasts, FollowRepository follows,
                          ChannelPostRepository posts, Clock clock) {
        this.users = users;
        this.broadcasts = broadcasts;
        this.follows = follows;
        this.posts = posts;
        this.clock = clock;
    }

    /** following: 비로그인(viewerId null)이면 false. liveBroadcastId: 지금 방송 중인 방송 id 또는 null. */
    public record Channel(Long id, String username, long followerCount, boolean following, Long liveBroadcastId,
                          Instant createdAt) {}

    /** 라이브 중인 채널 먼저, 그다음 이름순. */
    public record FollowedChannel(Long id, String username, Long liveBroadcastId, String liveTitle) {}

    @Transactional(readOnly = true)
    public Channel get(Long channelId, Long viewerId) {
        User u = requireUser(channelId);
        Long liveId = broadcasts.findFirstByOwnerIdAndStatusOrderByIdDesc(channelId, Broadcast.Status.LIVE)
                .map(Broadcast::getId).orElse(null);
        boolean following = viewerId != null && follows.existsByFollowerIdAndChannelId(viewerId, channelId);
        return new Channel(u.getId(), u.getUsername(), follows.countByChannelId(channelId), following, liveId,
                u.getCreatedAt());
    }

    /** 멱등: 이미 팔로우 중이어도 성공. 자기 자신은 400, 없는 사용자는 404. */
    @Transactional
    public void follow(Long userId, Long channelId) {
        if (channelId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없음");
        }
        requireUser(channelId);
        follows.follow(userId, channelId);
    }

    /** 팔로우하지 않았어도 성공(멱등). */
    @Transactional
    public void unfollow(Long userId, Long channelId) {
        follows.unfollow(userId, channelId);
    }

    @Transactional(readOnly = true)
    public List<FollowedChannel> following(Long userId) {
        List<Long> ids = follows.findAllByFollowerId(userId).stream().map(Follow::getChannelId).toList();
        // 한 사람이 방송을 여러 개 동시에 켰으면 가장 최근 것
        Map<Long, Broadcast> live = broadcasts.findAllByOwnerIdInAndStatus(ids, Broadcast.Status.LIVE).stream()
                .collect(Collectors.toMap(Broadcast::getOwnerId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));
        return users.findAllById(ids).stream()
                .map(u -> {
                    Broadcast b = live.get(u.getId());
                    return new FollowedChannel(u.getId(), u.getUsername(), b == null ? null : b.getId(),
                            b == null ? null : b.getTitle());
                })
                .sorted(Comparator.comparing((FollowedChannel c) -> c.liveBroadcastId() == null)
                        .thenComparing(FollowedChannel::username))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChannelPost> posts(Long channelId) {
        requireUser(channelId);
        return posts.findTop50ByChannelIdOrderByIdDesc(channelId);
    }

    /** 로그인한 사용자 누구나 어느 채널에나 쓸 수 있다. */
    @Transactional
    public ChannelPost write(Long userId, Long channelId, String content) {
        requireUser(channelId);
        return posts.save(new ChannelPost(channelId, userId, content.strip(), clock.instant()));
    }

    /** 작성자 또는 채널 주인만. 프론트가 버튼을 숨겨도 서버에서 다시 확인한다. */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        ChannelPost p = posts.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글 없음"));
        if (!p.canBeDeletedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자나 채널 주인만 삭제할 수 있음");
        }
        posts.delete(p);
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채널 없음"));
    }
}
