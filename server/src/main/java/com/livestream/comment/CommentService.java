package com.livestream.comment;

import com.livestream.broadcast.BroadcastService;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {
    private final CommentRepository comments;
    private final BroadcastService broadcasts;
    private final Clock clock;

    public CommentService(CommentRepository comments, BroadcastService broadcasts, Clock clock) {
        this.comments = comments;
        this.broadcasts = broadcasts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(Long broadcastId, long afterId) {
        broadcasts.get(broadcastId); // 없는 방송이면 404
        return comments.findTop200ByBroadcastIdAndIdGreaterThanOrderByIdAsc(broadcastId, afterId);
    }

    /** 로그인한 사용자 누구나 (방송 상태와 무관 — 종료된 방송에도 남길 수 있다). */
    @Transactional
    public Comment write(Long userId, Long broadcastId, String content) {
        broadcasts.get(broadcastId);
        return comments.save(new Comment(broadcastId, userId, content.strip(), clock.instant()));
    }

    @Transactional
    public Comment edit(Long userId, Long commentId, String content) {
        Comment c = requireAuthor(userId, commentId);
        c.edit(content.strip(), clock.instant());
        return c;
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        comments.delete(requireAuthor(userId, commentId));
    }

    /** 작성자 인가: 수정·삭제는 작성자 본인만 (방송 소유자도 남의 댓글은 못 바꾼다). */
    private Comment requireAuthor(Long userId, Long commentId) {
        Comment c = comments.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글 없음"));
        if (!c.isWrittenBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 작성자만 할 수 있음");
        }
        return c;
    }
}
