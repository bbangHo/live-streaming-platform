package com.livestream.broadcast;

import com.livestream.donation.Donation;
import com.livestream.donation.DonationRepository;
import com.livestream.user.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BroadcastService {
    private final BroadcastRepository broadcasts;
    private final DonationRepository donations;
    private final Clock clock;

    public BroadcastService(BroadcastRepository broadcasts, DonationRepository donations, Clock clock) {
        this.broadcasts = broadcasts;
        this.donations = donations;
        this.clock = clock;
    }

    /**
     * 최신순. 조건은 모두 선택(null이면 무시)이고 함께 쓰면 AND.
     * q: 제목 또는 방송자 username 부분일치(대소문자 무시).
     * shortcut: 페이지네이션 없음 — 방송이 수백 개를 넘으면 Pageable로 바꾼다
     */
    @Transactional(readOnly = true)
    public List<Broadcast> list(Broadcast.Status status, Broadcast.Category category, String q, Long ownerId) {
        Specification<Broadcast> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) where.add(cb.equal(root.get("status"), status));
            if (category != null) where.add(cb.equal(root.get("category"), category));
            if (ownerId != null) where.add(cb.equal(root.get("ownerId"), ownerId));
            if (q != null && !q.isBlank()) {
                // 입력의 %, _ 는 글자 그대로 찾는다
                String like = "%" + q.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                Subquery<Long> owners = query.subquery(Long.class);
                Root<User> u = owners.from(User.class);
                owners.select(u.get("id")).where(cb.like(cb.lower(u.get("username")), like, '!'));
                where.add(cb.or(cb.like(cb.lower(root.get("title")), like, '!'), root.get("ownerId").in(owners)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return broadcasts.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public Broadcast get(Long id) {
        return broadcasts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방송 없음"));
    }

    @Transactional
    public Broadcast create(Long userId, String title, String description, Broadcast.Category category) {
        return broadcasts.save(new Broadcast(userId, title, description, category));
    }

    @Transactional
    public Broadcast update(Long userId, Long id, String title, String description, Broadcast.Category category) {
        Broadcast b = requireOwner(userId, id);
        b.update(title, description, category);
        return b;
    }

    /** 승인된 후원이 있는 방송은 지우지 않는다 — 지우면 후원 기록(돈)이 함께 사라진다(donations FK가 CASCADE). */
    @Transactional
    public void delete(Long userId, Long id) {
        Broadcast b = requireOwner(userId, id);
        if (donations.existsByBroadcastIdAndStatus(id, Donation.Status.DONE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "후원 내역이 있는 방송은 삭제할 수 없음");
        }
        broadcasts.delete(b);
    }

    /**
     * 소유자의 방송 종료. 상태만 ENDED로 바꾸면 StreamStatusSync가 아직 붙어 있는 송출을 끊는다(kick).
     * 시작은 API가 없다 — 송출이 MediaMTX에 실제로 들어오면 StreamStatusSync가 LIVE로 바꾼다.
     */
    @Transactional
    public Broadcast stop(Long userId, Long id) {
        Broadcast b = requireOwner(userId, id);
        b.end(clock.instant());
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
