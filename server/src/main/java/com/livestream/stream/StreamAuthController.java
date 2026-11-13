package com.livestream.stream;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * MediaMTX authHTTPAddress가 부르는 내부 엔드포인트. 송출(publish) 요청마다 "이 사람이 이 경로로 송출해도 되나?"를 묻는다.
 * 외부에서는 nginx·Caddy가 /api/internal/ 을 막고, 직접 붙어도 hookSecret이 없으면 거부한다.
 */
@RestController
@RequestMapping("/api/internal/media")
public class StreamAuthController {
    private static final Logger log = LoggerFactory.getLogger(StreamAuthController.class);

    private final StreamAuthService auth;
    private final MediaProperties props;

    public StreamAuthController(StreamAuthService auth, MediaProperties props) {
        this.auth = auth;
        this.props = props;
    }

    /** MediaMTX가 보내는 JSON (필요한 필드만). token = WHIP 요청의 `Authorization: Bearer` 값. */
    public record AuthRequest(String action, String path, String token, String protocol, String ip) {}

    @PostMapping("/auth")
    public ResponseEntity<Void> authorize(@RequestParam(defaultValue = "") String secret, @RequestBody AuthRequest req) {
        if (!secretMatches(secret)) {
            log.warn("미디어 인증 요청 거부: hook secret 불일치 (ip={})", req.ip());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StreamAuthService.Decision d = auth.decide(req.action(), req.path(), req.token());
        if (!d.allowed()) {
            log.info("송출 거부: action={} path={} 이유={}", req.action(), req.path(), d.reason());
        }
        // 401이면 MediaMTX가 WHIP 클라이언트에 401을 그대로 돌려준다
        return ResponseEntity.status(d.allowed() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).build();
    }

    private boolean secretMatches(String given) {
        String expected = props.hookSecret();
        if (expected == null || expected.isBlank()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
