ALTER TABLE users
    ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE personal_desks
    ADD COLUMN default_message_visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX idx_delivery_desk_created
    ON personal_message_deliveries (desk_id, created_at);
CREATE INDEX idx_delivery_desk_unlock
    ON personal_message_deliveries (desk_id, unlock_at);

CREATE TABLE media_assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expected_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    uploaded_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_media_assets_object_key UNIQUE (object_key),
    CONSTRAINT fk_media_assets_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE advertisements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    creative_url VARCHAR(1000) NOT NULL,
    destination_url VARCHAR(1000) NOT NULL,
    placement VARCHAR(30) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE advertisement_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    advertisement_id BIGINT NOT NULL,
    user_id BIGINT,
    guest_id BIGINT,
    event_type VARCHAR(30) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    view_duration_ms BIGINT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ad_event_session_type UNIQUE (advertisement_id, event_type, session_id),
    CONSTRAINT fk_ad_events_advertisement FOREIGN KEY (advertisement_id) REFERENCES advertisements (id),
    CONSTRAINT fk_ad_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ad_events_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_ad_events_ad_type_time
    ON advertisement_events (advertisement_id, event_type, created_at);
CREATE INDEX idx_ad_events_user_time
    ON advertisement_events (user_id, created_at);
CREATE INDEX idx_ad_events_guest_time
    ON advertisement_events (guest_id, created_at);

CREATE TABLE traffic_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    guest_id BIGINT,
    event_type VARCHAR(40) NOT NULL,
    session_id VARCHAR(100),
    path VARCHAR(1000),
    referrer VARCHAR(1000),
    resource_type VARCHAR(100),
    resource_key VARCHAR(200),
    metadata_payload LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_traffic_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_traffic_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_traffic_events_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_traffic_type_time
    ON traffic_events (event_type, created_at);
CREATE INDEX idx_traffic_user_time
    ON traffic_events (user_id, created_at);
CREATE INDEX idx_traffic_guest_time
    ON traffic_events (guest_id, created_at);
