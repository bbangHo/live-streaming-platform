package com.livestream.auth;

import com.livestream.user.User;
import com.livestream.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwt;
    private final JwtProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtProvider jwt, JwtProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.props = props;
        this.clock = clock;
    }

    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

    @Transactional
    public User signup(String username, String rawPassword) {
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디");
        }
        return users.save(new User(username, passwordEncoder.encode(rawPassword)));
    }

    @Transactional
    public Tokens login(String username, String rawPassword) {
        User user = users.findByUsername(username)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                // 아이디 없음/비번 틀림을 구분하지 않는다(계정 존재 여부 노출 방지)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀림"));
        return issue(user.getId());
    }

    /** refresh token 회전: 쓴 토큰은 지우고 새 쌍을 준다. 재사용·만료·로그아웃된 토큰은 401. */
    @Transactional(noRollbackFor = ResponseStatusException.class) // 만료돼서 401을 내도 삭제는 반영
    public Tokens refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findById(hash(refreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token"));
        refreshTokens.delete(stored);
        if (stored.getExpiresAt().isBefore(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 refresh token");
        }
        return issue(stored.getUserId());
    }

    /** 로그아웃: refresh token 폐기. access token은 stateless라 남은 수명(기본 15분) 동안만 유효. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.deleteById(hash(refreshToken));
    }

    private Tokens issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(hash(refresh), userId, clock.instant().plus(props.refreshTtl())));
        return new Tokens(jwt.issueAccessToken(userId), refresh, "Bearer", jwt.accessTtlSeconds());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
