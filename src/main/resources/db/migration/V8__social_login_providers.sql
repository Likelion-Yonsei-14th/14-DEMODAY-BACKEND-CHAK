-- 로그인 제공자 일반화: 카카오 전용(kakao_id)이던 계정 식별자를 (provider, provider_user_id)로 바꾼다.
-- 기존 회원은 모두 KAKAO로 채워지고 식별값은 그대로 유지된다. 제공자가 달라도 같은 값이 겹칠 수 있으므로
-- 유일 제약은 값 단독이 아니라 (provider, provider_user_id) 쌍에 둔다.
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'KAKAO';
ALTER TABLE users RENAME COLUMN kakao_id TO provider_user_id;
ALTER TABLE users DROP CONSTRAINT uk_users_kakao_id;
ALTER TABLE users ADD CONSTRAINT uk_users_identity UNIQUE (provider, provider_user_id);
