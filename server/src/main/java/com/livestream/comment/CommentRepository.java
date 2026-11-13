package com.livestream.comment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /** 오래된 것부터 (채팅처럼 아래로 쌓인다). afterId보다 큰 것만 → 화면이 새 댓글만 이어 받는다 */
    List<Comment> findTop200ByBroadcastIdAndIdGreaterThanOrderByIdAsc(Long broadcastId, Long afterId);
}
