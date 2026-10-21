-- 친구를 위한 책상: 받는 사람이 넘겨받을 때 쓰는 링크 토큰, 넘겨받은 시각, 삭제 표시.
-- claim_token은 넘겨받기 전(UNCLAIMED)에만 값이 있고, 넘겨받거나 삭제하면 비워서 링크를 무효로 만든다.
ALTER TABLE personal_desks ADD COLUMN claim_token VARCHAR(36) NULL;
ALTER TABLE personal_desks ADD COLUMN claimed_at DATETIME(6) NULL;
ALTER TABLE personal_desks ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE personal_desks ADD CONSTRAINT uk_personal_desks_claim_token UNIQUE (claim_token);
CREATE INDEX idx_personal_desks_creator ON personal_desks (creator_id, claim_status);
