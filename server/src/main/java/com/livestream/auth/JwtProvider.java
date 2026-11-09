package com.livestream.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Access token(HS256, 짧은 수명) 발급·검증. subject = 사용자 id. */
@Component
public class JwtProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    private final SecretKey key;
    private final JwtProperties props;
    private final Clock clock;

    public JwtProvider(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        if (props.secret() == null || props.secret().isBlank()) {
            log.warn("JWT_SECRET 미설정 — 임의 키 사용. 재시작하면 발급된 access token이 모두 무효가 된다");
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8)); // 32바이트 미만이면 기동 실패
        }
    }

    public String issueAccessToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** 서명·만료가 유효하면 사용자 id, 아니면 empty. */
    public Optional<Long> parseUserId(String token) {
        try {
            String sub = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.valueOf(sub));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
