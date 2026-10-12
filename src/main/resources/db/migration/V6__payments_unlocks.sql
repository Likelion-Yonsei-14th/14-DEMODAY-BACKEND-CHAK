CREATE TABLE payment_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payer_user_id BIGINT,
    payer_guest_id BIGINT,
    total_amount INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_ref VARCHAR(100),
    consumed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_user FOREIGN KEY (payer_user_id) REFERENCES users (id),
    CONSTRAINT fk_order_guest FOREIGN KEY (payer_guest_id) REFERENCES guest_identities (id)
);

CREATE TABLE payment_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    item_code VARCHAR(40) NOT NULL,
    item_ref VARCHAR(100),
    quantity INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES payment_orders (id)
);

CREATE INDEX idx_order_user ON payment_orders (payer_user_id, created_at);
CREATE INDEX idx_order_guest ON payment_orders (payer_guest_id, created_at);

CREATE TABLE ad_unlocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    guest_id BIGINT,
    category VARCHAR(20) NOT NULL,
    asset_id VARCHAR(100) NOT NULL,
    ad_session_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_unlock_user UNIQUE (user_id, category, asset_id),
    CONSTRAINT uk_unlock_guest UNIQUE (guest_id, category, asset_id),
    CONSTRAINT fk_unlock_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_unlock_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);
