package com.livestream.comment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long broadcastId;

    private Long authorId;

    private String content;

    private Instant createdAt;

    /** 수정한 적이 없으면 null → 화면에 "(수정됨)" 표시용 */
    private Instant updatedAt;

    protected Comment() {}

    public Comment(Long broadcastId, Long authorId, String content, Instant now) {
        this.broadcastId = broadcastId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = now;
    }

    public boolean isWrittenBy(Long userId) {
        return authorId.equals(userId);
    }

    public void edit(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getBroadcastId() { return broadcastId; }
    public Long getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
