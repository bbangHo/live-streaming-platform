package com.livestream.broadcast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
    // shortcut: 페이지네이션 없음 — 방송이 수백 개를 넘으면 Pageable로 바꾼다
    List<Broadcast> findAllByOrderByIdDesc();
    List<Broadcast> findAllByStatusOrderByIdDesc(Broadcast.Status status);
}
