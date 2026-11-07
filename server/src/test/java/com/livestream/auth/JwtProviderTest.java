package com.livestream.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 단위 테스트: 스프링·DB 없이 토큰 검증 규칙만 본다. */
class JwtProviderTest {
    static final String SECRET = "test-secret-test-secret-test-secret-32b";
    static final Instant T0 = Instant.parse("2026-11-08T00:00:00Z");

    static JwtProvider at(Instant now, String secret) {
        return new JwtProvider(new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(14)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 발급한_토큰에서_사용자_id를_꺼낸다() {
        assertThat(at(T0, SECRET).parseUserId(at(T0, SECRET).issueAccessToken(42L))).contains(42L);
    }

    @Test
    void 만료된_토큰은_거부() {
        String token = at(T0, SECRET).issueAccessToken(42L);
        assertThat(at(T0.plus(Duration.ofMinutes(16)), SECRET).parseUserId(token)).isEmpty();
    }

    @Test
    void 다른_키로_서명된_토큰과_변조된_토큰은_거부() {
        String token = at(T0, "another-secret-another-secret-another-32b").issueAccessToken(42L);
        assertThat(at(T0, SECRET).parseUserId(token)).isEmpty();

        String mine = at(T0, SECRET).issueAccessToken(42L);
        String tampered = mine.substring(0, mine.length() - 2) + (mine.endsWith("AA") ? "BB" : "AA");
        assertThat(at(T0, SECRET).parseUserId(tampered)).isEmpty();
        assertThat(at(T0, SECRET).parseUserId("garbage")).isEmpty();
    }
}
