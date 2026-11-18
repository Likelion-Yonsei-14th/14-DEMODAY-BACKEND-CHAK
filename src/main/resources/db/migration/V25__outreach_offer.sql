-- 매장에 제시한 광고비와 그 근거(예상 수익, 객단가), 보낸 제안서 링크.
-- 전화하거나 답장할 때 무엇을 얼마에 제안했는지 바로 떠올리기 위해 둔다.
ALTER TABLE store_outreach ADD COLUMN offer_price INT NULL;
ALTER TABLE store_outreach ADD COLUMN expected_profit INT NULL;
ALTER TABLE store_outreach ADD COLUMN unit_price INT NULL;
ALTER TABLE store_outreach ADD COLUMN proposal_url VARCHAR(500) NULL;
