package com.livestream.channel;

import jakarta.persistence.*;
import java.time.Instant;

/** 채널 커뮤니티 게시글. channelId = 채널 주인(사용자) id. */
@Entity
@Table(name = "channel_posts")
public class ChannelPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long channelId;

    private Long authorId;

    private String content;

    private Instant createdAt;

    protected ChannelPost() {}

    public ChannelPost(Long channelId, Long authorId, String content, Instant now) {
        this.channelId = channelId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = now;
    }

    /** 작성자 또는 채널 주인. */
    public boolean canBeDeletedBy(Long userId) {
        return authorId.equals(userId) || channelId.equals(userId);
    }

    public Long getId() { return id; }
    public Long getChannelId() { return channelId; }
    public Long getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
