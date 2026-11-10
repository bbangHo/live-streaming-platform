package com.livestream.broadcast;

import com.livestream.auth.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MediaMTX authHTTPAddress. MediaMTX가 송출·시청 요청마다 여기로 물어보고 2xx면 허용한다.
 * 송출(publish)만 막는다: WHIP 요청의 `Authorization: Bearer <access token>`이 그 방송 소유자여야 한다.
 * 시청(read 등)은 공개. nginx·Caddy는 /api/ 만 넘기므로 이 경로는 외부 프록시에 노출되지 않는다.
 */
@RestController
public class MediaAuthController {
    private final BroadcastService service;
    private final JwtProvider jwt;

    public MediaAuthController(BroadcastService service, JwtProvider jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    /** MediaMTX가 보내는 본문 중 쓰는 필드만. path = "live/{streamKey}". */
    public record MediaAuthRequest(String action, String path, String token) {}

    @PostMapping("/internal/media-auth")
    public ResponseEntity<Void> auth(@RequestBody MediaAuthRequest req) {
        if (!"publish".equals(req.action())) return ResponseEntity.ok().build();
        boolean ok = req.path() != null && req.path().startsWith("live/") && req.token() != null
                && jwt.parseUserId(req.token())
                        .map(userId -> service.canPublish(userId, req.path().substring("live/".length())))
                        .orElse(false);
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).build();
    }
}
