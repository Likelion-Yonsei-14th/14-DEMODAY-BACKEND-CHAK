-- 회원이 직접 고친 이름과 프로필 사진.
-- profile_customized: 직접 고친 이름은 다음 로그인 때 카카오·구글 이름으로 덮어쓰지 않는다.
ALTER TABLE users
    ADD COLUMN profile_customized BOOLEAN NOT NULL DEFAULT FALSE;

-- 직접 올린 프로필 사진(작은 정사각형 사진). avatar_key는 올릴 때마다 새로 만드는 난수라서 주소를 바꾸면 브라우저 캐시도 새로 받는다.
CREATE TABLE user_avatars (
    user_id BIGINT NOT NULL,
    avatar_key VARCHAR(36) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    image_base64 LONGTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_avatars_key UNIQUE (avatar_key),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id)
);
