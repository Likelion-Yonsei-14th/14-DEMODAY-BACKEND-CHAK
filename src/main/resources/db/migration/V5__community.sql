CREATE TABLE message_reactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_id BIGINT,
    guest_id BIGINT,
    reaction_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reaction_message_user UNIQUE (message_id, user_id),
    CONSTRAINT uk_reaction_message_guest UNIQUE (message_id, guest_id),
    CONSTRAINT fk_reaction_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reaction_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);

CREATE TABLE message_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    desk_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    scope VARCHAR(20) NOT NULL,
    source_message_id BIGINT,
    reply_text VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reply_desk FOREIGN KEY (desk_id) REFERENCES personal_desks (id),
    CONSTRAINT fk_reply_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_reply_source FOREIGN KEY (source_message_id) REFERENCES messages (id)
);

CREATE TABLE message_reply_targets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reply_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reply_target_message UNIQUE (message_id),
    CONSTRAINT fk_reply_target_reply FOREIGN KEY (reply_id) REFERENCES message_replies (id),
    CONSTRAINT fk_reply_target_message FOREIGN KEY (message_id) REFERENCES messages (id)
);

CREATE TABLE message_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    reporter_user_id BIGINT,
    reporter_guest_id BIGINT,
    reason VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_report_message_user UNIQUE (message_id, reporter_user_id),
    CONSTRAINT uk_report_message_guest UNIQUE (message_id, reporter_guest_id),
    CONSTRAINT fk_report_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_report_user FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_report_guest FOREIGN KEY (reporter_guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_report_status_time ON message_reports (status, created_at);

CREATE TABLE desk_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    desk_id BIGINT NOT NULL,
    author_user_id BIGINT,
    author_guest_id BIGINT,
    display_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_block_desk_user UNIQUE (desk_id, author_user_id),
    CONSTRAINT uk_block_desk_guest UNIQUE (desk_id, author_guest_id),
    CONSTRAINT fk_block_desk FOREIGN KEY (desk_id) REFERENCES personal_desks (id),
    CONSTRAINT fk_block_user FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT fk_block_guest FOREIGN KEY (author_guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_messages_author_user ON messages (author_user_id, created_at);
CREATE INDEX idx_messages_author_guest ON messages (author_guest_id, created_at);
