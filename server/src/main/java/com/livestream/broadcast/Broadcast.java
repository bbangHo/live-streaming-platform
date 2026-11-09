package com.livestream.broadcast;

import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "broadcasts")
public class Broadcast {
    /** READY(대기) → LIVE(방송 중) → ENDED(종료). ENDED는 끝 — 다시 방송하려면 새로 만든다. */
    public enum Status { READY, LIVE, ENDED }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    private String title;

    private String description;

    /** MediaMTX 경로 live/{streamKey}. 송출(WHIP)·시청(HLS) 주소에 같이 쓰인다. */
    private String streamKey;

    @Enumerated(EnumType.STRING)
    private Status status = Status.READY;

    private Instant createdAt = Instant.now();

    protected Broadcast() {}

    public Broadcast(Long ownerId, String title, String description) {
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        this.streamKey = HexFormat.of().formatHex(bytes);
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void start() {
        if (status != Status.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "대기 중인 방송만 시작할 수 있음 (현재 " + status + ")");
        }
        status = Status.LIVE;
    }

    public void stop() {
        if (status != Status.LIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "방송 중일 때만 종료할 수 있음 (현재 " + status + ")");
        }
        status = Status.ENDED;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStreamKey() { return streamKey; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
