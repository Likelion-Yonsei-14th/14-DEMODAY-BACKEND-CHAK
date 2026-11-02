-- 로그인한 사용자가 즐겨찾기한 친구 책상. 사용자 한 명이 같은 책상을 한 번만 담을 수 있다.
-- 책상은 지워도 행이 남으므로(soft delete) 조회할 때 지워진 책상은 빼고 보여 준다.
CREATE TABLE favorite_desks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    desk_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorite_desks_user_desk UNIQUE (user_id, desk_id),
    CONSTRAINT fk_favorite_desks_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_favorite_desks_desk FOREIGN KEY (desk_id) REFERENCES personal_desks (id)
);

CREATE INDEX idx_favorite_desks_user_created ON favorite_desks (user_id, created_at);
