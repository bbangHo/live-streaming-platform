package com.livestream.broadcast;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BroadcastService {
    private final BroadcastRepository broadcasts;

    public BroadcastService(BroadcastRepository broadcasts) {
        this.broadcasts = broadcasts;
    }

    /** status가 null이면 전체. */
    @Transactional(readOnly = true)
    public List<Broadcast> list(Broadcast.Status status) {
        return status == null ? broadcasts.findAllByOrderByIdDesc() : broadcasts.findAllByStatusOrderByIdDesc(status);
    }

    @Transactional(readOnly = true)
    public Broadcast get(Long id) {
        return broadcasts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송 없음"));
    }

    @Transactional
    public Broadcast create(Long userId, String title, String description) {
        return broadcasts.save(new Broadcast(userId, title, description));
    }

    @Transactional
    public Broadcast update(Long userId, Long id, String title, String description) {
        Broadcast b = requireOwner(userId, id);
        b.update(title, description);
        return b;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        broadcasts.delete(requireOwner(userId, id));
    }

    /** 송출 제어. 실제 WHIP 연결 종료·MediaMTX 동기화는 D6~D8. */
    @Transactional
    public Broadcast start(Long userId, Long id) {
        Broadcast b = requireOwner(userId, id);
        b.start();
        return b;
    }

    @Transactional
    public Broadcast stop(Long userId, Long id) {
        Broadcast b = requireOwner(userId, id);
        b.stop();
        return b;
    }

    /** 소유자 인가: 변경 메서드는 모두 여기를 거친다. 프론트가 버튼을 숨겨도 서버에서 다시 확인한다. */
    private Broadcast requireOwner(Long userId, Long id) {
        Broadcast b = get(id);
        if (!b.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 소유자만 할 수 있음");
        }
        return b;
    }
}
