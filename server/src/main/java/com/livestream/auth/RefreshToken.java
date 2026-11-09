package com.livestream.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private String tokenHash;

    private Long userId;

    private Instant expiresAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
