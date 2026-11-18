-- 혜택함 제휴 광고. 광고 계약한 매장 혜택(sponsored)을 목록 위에 사람마다 다른 순서로 보여 주고,
-- 지도에서 찾을 수 있게 주소와 좌표를 둔다. 노출·클릭은 discount_events에 쌓는다.
ALTER TABLE discounts ADD COLUMN sponsored BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE discounts ADD COLUMN address VARCHAR(200) NULL;
ALTER TABLE discounts ADD COLUMN latitude DOUBLE NULL;
ALTER TABLE discounts ADD COLUMN longitude DOUBLE NULL;

CREATE TABLE discount_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    discount_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    placement VARCHAR(20) NOT NULL,
    user_id BIGINT NULL,
    guest_id BIGINT NULL,
    region VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_discount_events_discount FOREIGN KEY (discount_id) REFERENCES discounts (id) ON DELETE CASCADE
);
CREATE INDEX idx_discount_events_discount ON discount_events (discount_id, event_type, created_at);
CREATE INDEX idx_discount_events_user ON discount_events (user_id, created_at);
CREATE INDEX idx_discount_events_guest ON discount_events (guest_id, created_at);
