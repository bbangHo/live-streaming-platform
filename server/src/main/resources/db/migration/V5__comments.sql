-- 선택 기능: 방송 댓글. 방송을 지우면 댓글도 지운다. 작성자 탈퇴 기능은 없지만 FK로 정합성 유지
CREATE TABLE comments (
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT        NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    author_id    BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content      VARCHAR(500)  NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ
);
CREATE INDEX idx_comments_broadcast ON comments (broadcast_id, id);
