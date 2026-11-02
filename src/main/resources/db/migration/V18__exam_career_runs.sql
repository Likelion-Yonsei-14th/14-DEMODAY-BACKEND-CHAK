-- 수험생 커리어 시뮬레이터 한 판의 시작과 끝. 통계용으로만 쓰고 이름·연락처·계정·IP는 저장하지 않는다.
-- run_id는 브라우저가 판마다 만드는 난수라서, 같은 값으로 다시 보내면 같은 판을 고친다(끝났음 표시).
CREATE TABLE exam_career_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    track VARCHAR(12) NOT NULL,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    attempt INT NULL,
    final_average DECIMAL(3, 1) NULL,
    enrolled VARCHAR(20) NULL,
    title VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_career_runs_run_id UNIQUE (run_id)
);

CREATE INDEX idx_exam_career_runs_created ON exam_career_runs (created_at);
