CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kakao_id VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    email VARCHAR(320),
    profile_image_url VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
);

CREATE TABLE guest_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guest_key VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_guest_identities_guest_key UNIQUE (guest_key)
);

CREATE TABLE personal_desks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT,
    creator_id BIGINT NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    claim_status VARCHAR(20) NOT NULL,
    read_mode_type VARCHAR(20) NOT NULL,
    daily_unlock_time TIME(6),
    capsule_unlock_at DATETIME(6),
    timezone VARCHAR(40) NOT NULL,
    public_feed_enabled BOOLEAN NOT NULL,
    room_closed BOOLEAN NOT NULL,
    supporter_token VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_desks_supporter_token UNIQUE (supporter_token),
    CONSTRAINT fk_personal_desks_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_personal_desks_creator FOREIGN KEY (creator_id) REFERENCES users (id)
);

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_user_id BIGINT,
    author_guest_id BIGINT,
    nickname_override VARCHAR(50),
    kind VARCHAR(20) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    schema_version INTEGER NOT NULL,
    card_payload LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    read_at DATETIME(6),
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_author_user FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT fk_messages_author_guest FOREIGN KEY (author_guest_id) REFERENCES guest_identities (id)
);

CREATE TABLE personal_message_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    desk_id BIGINT NOT NULL,
    unlock_at DATETIME(6) NOT NULL,
    preclaim_backlog BOOLEAN NOT NULL,
    basketed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_message_deliveries_message UNIQUE (message_id),
    CONSTRAINT fk_personal_message_deliveries_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_personal_message_deliveries_desk FOREIGN KEY (desk_id) REFERENCES personal_desks (id)
);

CREATE TABLE desk_objects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    desk_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    representation_type VARCHAR(30) NOT NULL,
    asset_id VARCHAR(100),
    color VARCHAR(30),
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    rotation DOUBLE NOT NULL,
    scale DOUBLE NOT NULL,
    z_index INTEGER NOT NULL,
    metadata_payload LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_desk_objects_message UNIQUE (message_id),
    CONSTRAINT fk_desk_objects_desk FOREIGN KEY (desk_id) REFERENCES personal_desks (id),
    CONSTRAINT fk_desk_objects_message FOREIGN KEY (message_id) REFERENCES messages (id)
);

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
