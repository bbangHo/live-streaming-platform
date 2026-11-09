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

    @Transactional(readOnly = true)
    public List<Broadcast> list() {
        return broadcasts.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Broadcast get(Long id) {
        return broadcasts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송 없음"));
    }

    @Transactional
    public Broadcast create(Long userId, String title) {
        return broadcasts.save(new Broadcast(userId, title));
    }

    @Transactional
    public Broadcast rename(Long userId, Long id, String title) {
        Broadcast b = requireOwner(userId, id);
        b.rename(title);
        return b;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        broadcasts.delete(requireOwner(userId, id));
    }

    /** 송출 제어(시작/종료). 실제 WHIP 연결 종료·MediaMTX 동기화는 D6~D8. */
    @Transactional
    public Broadcast changeStatus(Long userId, Long id, Broadcast.Status status) {
        Broadcast b = requireOwner(userId, id);
        b.changeStatus(status);
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
