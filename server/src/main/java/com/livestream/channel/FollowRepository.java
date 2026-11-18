package com.livestream.channel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    /** 이미 있으면 아무것도 안 한다 — 동시에 두 번 와도 한 행(UNIQUE). */
    @Modifying
    @Query(value = "INSERT INTO follows (follower_id, channel_id) VALUES (:followerId, :channelId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void follow(Long followerId, Long channelId);

    @Modifying
    @Query("delete from Follow f where f.followerId = :followerId and f.channelId = :channelId")
    void unfollow(Long followerId, Long channelId);

    long countByChannelId(Long channelId);
    boolean existsByFollowerIdAndChannelId(Long followerId, Long channelId);
    List<Follow> findAllByFollowerId(Long followerId);
}
