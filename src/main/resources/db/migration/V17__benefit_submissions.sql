-- 혜택 시안 신청: /promotion/apply에서 제휴 업체가 보낸 혜택 정보. 담당자 연락처가 있어 어드민에서만 보고 1년 뒤 삭제한다.
-- 설문 문항이 시즌마다 바뀔 수 있어 답변 전체는 answers(JSON)에 두고, 목록에 필요한 것만 칼럼으로 뺀다.
CREATE TABLE benefit_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    headline VARCHAR(40) NOT NULL,
    answers LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at DATETIME(6) NOT NULL,
    handled_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_benefit_submissions_status_created ON benefit_submissions (status, created_at);
