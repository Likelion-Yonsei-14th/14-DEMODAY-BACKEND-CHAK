-- 답장 알림(웹 푸시) 구독. 브라우저가 알려 준 주소(endpoint)와 암호화 키를 저장한다.
-- 구독한 사람은 로그인 회원(user_id) 또는 쿠키로 구분되는 비회원(guest_id) 중 하나다.
CREATE TABLE push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    guest_id BIGINT NULL,
    endpoint VARCHAR(500) NOT NULL,
    p256dh VARCHAR(100) NOT NULL,
    auth VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_success_at DATETIME(6) NULL,
    failure_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_push_subscriptions_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
CREATE INDEX idx_push_subscriptions_guest ON push_subscriptions (guest_id);
