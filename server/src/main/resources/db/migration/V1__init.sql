CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- refresh token 원문은 저장하지 않고 SHA-256 해시만 둔다. 로그아웃 = 행 삭제
CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

-- 최소 스텁: 인가(소유자 검증) 패턴용. 전체 CRUD 필드는 D5에서 확장
CREATE TABLE broadcasts (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    status     VARCHAR(10)  NOT NULL DEFAULT 'READY',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_broadcasts_owner ON broadcasts (owner_id);
