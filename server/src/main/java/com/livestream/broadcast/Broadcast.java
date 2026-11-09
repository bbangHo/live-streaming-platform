package com.livestream.broadcast;

import jakarta.persistence.*;
import java.time.Instant;

/** 최소 스텁(D3): 소유자 인가 패턴용. 설명·썸네일·스트림 키 등은 D5에서 추가. */
@Entity
@Table(name = "broadcasts")
public class Broadcast {
    public enum Status { READY, LIVE, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    private String title;

    @Enumerated(EnumType.STRING)
    private Status status = Status.READY;

    private Instant createdAt = Instant.now();

    protected Broadcast() {}

    public Broadcast(Long ownerId, String title) {
        this.ownerId = ownerId;
        this.title = title;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public void rename(String title) { this.title = title; }
    public void changeStatus(Status status) { this.status = status; }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
