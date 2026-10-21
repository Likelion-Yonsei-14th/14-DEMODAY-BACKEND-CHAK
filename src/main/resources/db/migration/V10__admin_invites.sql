-- 운영자 초대: 이메일로 운영자 권한을 미리 허용한다. 구글이 확인한 같은 이메일로 로그인하면 운영자가 된다.
-- created_by가 비어 있으면 최초 설정(부트스트랩)이다.
CREATE TABLE admin_invites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    consumed_user_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_invite_email UNIQUE (email),
    CONSTRAINT fk_admin_invite_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_admin_invite_consumer FOREIGN KEY (consumed_user_id) REFERENCES users (id)
);

-- 첫 운영자. 이 이메일의 구글 계정으로 로그인하면 운영자 권한이 켜진다.
INSERT INTO admin_invites (email, created_by, created_at) VALUES ('mj0324@yonsei.ac.kr', NULL, CURRENT_TIMESTAMP(6));
