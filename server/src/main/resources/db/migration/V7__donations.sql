-- 선택 기능: 도네이션. 주문(PENDING)을 먼저 만들고, PG 승인이 서버에서 확인되면 DONE으로 한 번만 바뀐다.
-- 합계 = DONE 행의 amount 합 (별도 카운터를 올리지 않으므로 같은 결제가 두 번 더해질 수 없다)
CREATE TABLE donations (
    id           BIGSERIAL PRIMARY KEY,
    order_id     VARCHAR(64)  NOT NULL UNIQUE,       -- 우리가 만든 주문 번호 (PG에 넘김)
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    donor_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount       INTEGER      NOT NULL CHECK (amount > 0),
    message      VARCHAR(200),
    status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING',   -- PENDING → DONE | FAILED
    provider     VARCHAR(10)  NOT NULL,              -- toss | test
    payment_key  VARCHAR(200) UNIQUE,                -- PG 결제 키: 같은 결제가 두 주문에 붙을 수 없다
    fail_reason  VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at  TIMESTAMPTZ
);
CREATE INDEX idx_donations_broadcast_status ON donations (broadcast_id, status);
