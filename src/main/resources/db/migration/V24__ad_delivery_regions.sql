-- 광고 지역 맞춤: 대상 시·도 이름을 쉼표로("서울,경기"). 비어 있으면 전국.
ALTER TABLE advertisements ADD COLUMN target_regions VARCHAR(200) NULL;

-- 광고 노출·클릭이 일어난 시·도 이름(지역별 도달 보고용). 모르면 비움. IP는 저장하지 않는다.
ALTER TABLE advertisement_events ADD COLUMN region VARCHAR(20) NULL;
