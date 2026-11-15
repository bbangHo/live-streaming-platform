package com.livestream.stream;

import com.livestream.auth.JwtProvider;
import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastRepository;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송출 인가: 등록된 방송의 스트림 키로 송출하려면 그 방송 소유자의 access token이 필요하다.
 * 스트림 키는 시청 주소(HLS)라 공개돼 있으므로 키만으로는 송출할 수 없다.
 */
@Service
public class StreamAuthService {
    static final Pattern LIVE_PATH = Pattern.compile("^live/([A-Za-z0-9_-]+)$");

    private final BroadcastRepository broadcasts;
    private final JwtProvider jwt;
    private final MediaProperties props;

    public StreamAuthService(BroadcastRepository broadcasts, JwtProvider jwt, MediaProperties props) {
        this.broadcasts = broadcasts;
        this.jwt = jwt;
        this.props = props;
    }

    public record Decision(boolean allowed, String reason) {
        static Decision allow() { return new Decision(true, null); }
        static Decision deny(String reason) { return new Decision(false, reason); }
    }

    /** "live/abc" → "abc", 형식이 다르면 empty. */
    static Optional<String> streamKeyOf(String path) {
        if (path == null) return Optional.empty();
        Matcher m = LIVE_PATH.matcher(path);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Decision decide(String action, String path, String token) {
        // 시청(HLS·WHEP)은 공개. MediaMTX 설정에서도 read는 인증 제외지만 여기서도 허용으로 둔다
        if ("read".equals(action) || "playback".equals(action)) return Decision.allow();
        if (!"publish".equals(action)) return Decision.deny("허용하지 않는 동작: " + action);

        Optional<String> key = streamKeyOf(path);
        if (key.isEmpty()) return Decision.deny("live/<스트림 키> 형식이 아닌 경로");

        Optional<Broadcast> found = broadcasts.findByStreamKey(key.get());
        if (found.isEmpty()) {
            return props.devBypass() ? Decision.allow() : Decision.deny("등록되지 않은 스트림 키");
        }
        Broadcast b = found.get();
        Optional<Long> userId = token == null || token.isBlank() ? Optional.empty() : jwt.parseUserId(token);
        if (userId.isEmpty()) return Decision.deny("로그인 토큰 없음 또는 만료");
        if (!b.isOwnedBy(userId.get())) return Decision.deny("방송 소유자가 아님");
        if (!b.acceptsPublish()) return Decision.deny("종료된 방송");
        return Decision.allow();
    }
}
