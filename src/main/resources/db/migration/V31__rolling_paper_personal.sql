-- 개인 롤링페이퍼: 만든 사람의 종이 한 장에 링크로 온 사람들이 한마디씩 남긴다.
-- 기존 반 롤링페이퍼는 CLASS.
ALTER TABLE rolling_paper_classes ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'CLASS';
