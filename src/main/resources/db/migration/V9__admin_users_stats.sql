-- 어드민: 마지막 로그인·탈퇴 표시, 접속 지역(국가·시도 코드, IP는 저장하지 않음), 관리자 작업 기록.
ALTER TABLE users ADD COLUMN last_login_at DATETIME(6) NULL;
ALTER TABLE users ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE traffic_events ADD COLUMN country VARCHAR(2) NULL;
ALTER TABLE traffic_events ADD COLUMN region VARCHAR(40) NULL;
CREATE INDEX idx_traffic_type_time_region ON traffic_events (event_type, created_at, region);

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    detail VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_user_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_target ON admin_audit_logs (target_type, target_id, created_at);
