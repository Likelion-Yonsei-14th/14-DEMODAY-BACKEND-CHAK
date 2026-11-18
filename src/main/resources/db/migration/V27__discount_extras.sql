-- 광고주마다 다른 이벤트 내용·조건·증정품을 자유롭게 적는 칸. 한 줄에 하나씩 "이름<TAB>내용".
ALTER TABLE discounts ADD COLUMN extras VARCHAR(2000) NULL;
-- 받는 방법을 고정 목록이 아닌 자유 입력으로 받는다("네이버 예약 후 방문" 등).
ALTER TABLE discounts MODIFY COLUMN how_to VARCHAR(50) NULL;
