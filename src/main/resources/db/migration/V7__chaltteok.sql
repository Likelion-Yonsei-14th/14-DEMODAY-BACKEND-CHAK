-- 찰떡: 충전식 포인트(1원 = 1찰떡). 지갑은 회원 계정 단위이며 돈과 직결되므로
-- 불변 조건(잔액 ≥ 0, 한 결제는 한 번만 반영, 장부 부호)을 애플리케이션이 아니라 DB 제약으로도 강제한다.

-- 잔액. 변경은 항상 행 잠금(SELECT ... FOR UPDATE) 아래에서 하고, version은 잠금 없이 쓰는 실수를 잡는 안전망이다.
CREATE TABLE chaltteok_wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    customer_key VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallet_user UNIQUE (user_id),
    CONSTRAINT uk_wallet_customer_key UNIQUE (customer_key),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_wallet_balance CHECK (balance >= 0)
);

-- 토스 결제 주문. READY(발급) → CONFIRMING(승인 요청 중, 한 요청만 선점) → DONE(잔액 반영).
-- status 값은 enum(ChargeStatus)과 선점 쿼리로만 바뀐다(IN 목록 CHECK는 테스트용 H2 2.4의 버그로 넣지 않음).
-- payment_key 유니크: 같은 결제를 두 주문에 쓰는 것을 막는다. 선점은 claimed_at으로 시간 제한을 둔다.
CREATE TABLE chaltteok_charges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_key VARCHAR(200),
    result_balance BIGINT,
    claimed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_charge_order_id UNIQUE (order_id),
    CONSTRAINT uk_charge_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_charge_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_charge_amount CHECK (amount BETWEEN 300 AND 50000),
    CONSTRAINT ck_charge_done CHECK (
        status <> 'DONE' OR (payment_key IS NOT NULL AND result_balance IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE INDEX idx_charge_user_status_time ON chaltteok_charges (user_id, status, created_at);

-- 잔액 변동 장부(append-only). 충전은 +, 구매는 −이고 balance_after로 잔액을 사후 검증할 수 있다.
-- uk_ledger_entry: 한 충전 주문·한 구매 주문은 장부에 정확히 한 번만 기록된다.
-- uk_ledger_idempotency: 같은 Idempotency-Key로 구매를 다시 보내도 이중 차감되지 않는다.
CREATE TABLE chaltteok_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    ref VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(64),
    request_hash VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_entry UNIQUE (entry_type, ref),
    CONSTRAINT uk_ledger_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_ledger_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_ledger_sign CHECK (
        (entry_type = 'CHARGE' AND amount > 0) OR (entry_type = 'PURCHASE' AND amount < 0)),
    CONSTRAINT ck_ledger_balance CHECK (balance_after >= 0)
);

CREATE INDEX idx_ledger_user_time ON chaltteok_ledger (user_id, created_at, id);
