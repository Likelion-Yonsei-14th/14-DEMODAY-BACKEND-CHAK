-- 연락 대상이 매장 한 곳인지, 여러 지점을 둔 브랜드(본사)인지 구분한다. 기존 기록은 모두 매장이다.
ALTER TABLE store_outreach ADD COLUMN target_kind VARCHAR(10) NOT NULL DEFAULT 'STORE';
