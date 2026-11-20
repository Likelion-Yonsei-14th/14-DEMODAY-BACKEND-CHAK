-- 졸업 롤링페이퍼. 반을 만든 사람과 링크로 들어온 친구가 각자 롤링페이퍼(사물함) 하나를 걸고,
-- 서로의 종이에 한마디씩 남긴다. 참여자는 로그인 회원(user_id) 또는 쿠키로 구분되는 비회원(guest_id) 중 하나다.
CREATE TABLE rolling_paper_classes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 초대 링크에 쓰는 추측하기 어려운 코드
    code VARCHAR(32) NOT NULL,
    name VARCHAR(20) NOT NULL,
    -- INSTANT: 자기 종이를 바로 본다, ON_END: 만든 사람이 종료하면 본다
    reveal_mode VARCHAR(20) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rolling_paper_classes_code UNIQUE (code)
);

CREATE TABLE rolling_paper_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    guest_id BIGINT NULL,
    display_name VARCHAR(12) NOT NULL,
    -- 프론트의 편지지·기본 배경 id (예: bg-stationery-note-pink)
    paper_design VARCHAR(48) NOT NULL,
    creator BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rolling_paper_members_user UNIQUE (class_id, user_id),
    CONSTRAINT uk_rolling_paper_members_guest UNIQUE (class_id, guest_id),
    CONSTRAINT fk_rolling_paper_members_class FOREIGN KEY (class_id) REFERENCES rolling_paper_classes (id),
    CONSTRAINT fk_rolling_paper_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_rolling_paper_members_guest FOREIGN KEY (guest_id) REFERENCES guest_identities (id)
);

CREATE INDEX idx_rolling_paper_members_user ON rolling_paper_members (user_id);
CREATE INDEX idx_rolling_paper_members_guest ON rolling_paper_members (guest_id);

-- 한 사람이 한 친구의 종이에 남기는 한마디는 하나다. 다시 쓰면 고치거나 자리를 옮긴다.
CREATE TABLE rolling_paper_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    paper_member_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    note_text VARCHAR(60) NOT NULL,
    pen VARCHAR(16) NOT NULL,
    stamp VARCHAR(16) NOT NULL,
    -- 종이에서 글의 가운데 위치(종이 너비·높이의 %)
    pos_x DECIMAL(5, 2) NOT NULL,
    pos_y DECIMAL(5, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rolling_paper_notes_pair UNIQUE (paper_member_id, author_member_id),
    CONSTRAINT fk_rolling_paper_notes_class FOREIGN KEY (class_id) REFERENCES rolling_paper_classes (id),
    CONSTRAINT fk_rolling_paper_notes_paper FOREIGN KEY (paper_member_id) REFERENCES rolling_paper_members (id),
    CONSTRAINT fk_rolling_paper_notes_author FOREIGN KEY (author_member_id) REFERENCES rolling_paper_members (id)
);

CREATE INDEX idx_rolling_paper_notes_class ON rolling_paper_notes (class_id);

-- 교실 칠판: 반 친구 누구나 한 줄씩 적는다.
CREATE TABLE rolling_paper_board_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    entry_text VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rolling_paper_board_class FOREIGN KEY (class_id) REFERENCES rolling_paper_classes (id),
    CONSTRAINT fk_rolling_paper_board_author FOREIGN KEY (author_member_id) REFERENCES rolling_paper_members (id)
);

CREATE INDEX idx_rolling_paper_board_class ON rolling_paper_board_entries (class_id, created_at);
