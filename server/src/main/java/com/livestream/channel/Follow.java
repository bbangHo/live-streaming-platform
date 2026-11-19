package com.livestream.channel;

import jakarta.persistence.*;
import java.time.Instant;

/** followerId가 channelId(= 사용자)를 팔로우. 추가는 FollowRepository.follow(멱등 INSERT)로만. */
@Entity
@Table(name = "follows")
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long followerId;

    private Long channelId;

    private Instant createdAt;

    protected Follow() {}

    public Long getFollowerId() { return followerId; }
    public Long getChannelId() { return channelId; }
}
