-- 광고 상담 신청: /promotion/apply에서 브랜드 담당자가 남긴 신청. 개인정보(담당자 이름·이메일·연락처)가 있어 1년 뒤 삭제한다.
CREATE TABLE promotion_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone VARCHAR(30) NULL,
    category VARCHAR(50) NULL,
    interest VARCHAR(20) NULL,
    message VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at DATETIME(6) NOT NULL,
    handled_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_promotion_inquiries_status_created ON promotion_inquiries (status, created_at);
CREATE INDEX idx_promotion_inquiries_email_created ON promotion_inquiries (email, created_at);
