-- 수능 끝 D+1 코스 짜기 미니게임의 응답. 통계용으로만 쓰고 이름·연락처·계정·IP는 저장하지 않는다.
-- response_id는 브라우저가 판마다 만드는 난수라서, 같은 값으로 다시 보내면 같은 응답을 고친다(공유했음 표시).
CREATE TABLE d1_course_responses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id VARCHAR(36) NOT NULL,
    start_region VARCHAR(20) NOT NULL,
    start_district VARCHAR(20) NULL,
    area VARCHAR(20) NOT NULL,
    activity1 VARCHAR(20) NOT NULL,
    activity2 VARCHAR(20) NOT NULL,
    activity3 VARCHAR(20) NOT NULL,
    companion VARCHAR(20) NOT NULL,
    budget VARCHAR(20) NOT NULL,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_d1_course_responses_response_id UNIQUE (response_id)
);

CREATE INDEX idx_d1_course_responses_created ON d1_course_responses (created_at);
