package com.livestream.recording;

import jakarta.persistence.*;
import java.time.Instant;

/** 녹화 파일 하나(MediaMTX 세그먼트). 방송 한 번에 여러 개일 수 있다 — 송출이 끊겼다 다시 붙거나 1시간이 넘으면. */
@Entity
@Table(name = "recordings")
public class Recording {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long broadcastId;

    private String streamPath;

    private String filePath;

    private Instant startedAt;

    private long durationMs;

    private Instant createdAt;

    protected Recording() {}

    public Recording(Long broadcastId, String streamPath, String filePath, Instant startedAt, long durationMs, Instant now) {
        this.broadcastId = broadcastId;
        this.streamPath = streamPath;
        this.filePath = filePath;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getBroadcastId() { return broadcastId; }
    public String getStreamPath() { return streamPath; }
    public String getFilePath() { return filePath; }
    public Instant getStartedAt() { return startedAt; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
