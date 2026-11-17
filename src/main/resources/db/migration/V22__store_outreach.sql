-- 팀원이 광고 제휴를 위해 연락하는 매장 목록. 운영자끼리 누가 어디에 연락했고 어떻게 됐는지 공유한다.
CREATE TABLE store_outreach (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NULL,
    region VARCHAR(50) NULL,
    channel VARCHAR(20) NOT NULL,
    contact VARCHAR(300) NULL,
    assignee VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    note VARCHAR(2000) NULL,
    last_contacted_on DATE NULL,
    updated_by_name VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_store_outreach_status_updated ON store_outreach (status, updated_at);
