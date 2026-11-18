-- 치지직풍 확장: 방송 카테고리, 채널 팔로우, 채널 커뮤니티 게시글
ALTER TABLE broadcasts ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'TALK';

-- (follower_id, channel_id) 하나만 — 팔로우 PUT은 ON CONFLICT DO NOTHING으로 멱등
CREATE TABLE follows (
    id          BIGSERIAL PRIMARY KEY,
    follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_id, channel_id),
    CHECK (follower_id <> channel_id)
);
CREATE INDEX idx_follows_channel ON follows (channel_id);

CREATE TABLE channel_posts (
    id         BIGSERIAL PRIMARY KEY,
    channel_id BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    author_id  BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_channel_posts_channel ON channel_posts (channel_id, id);
