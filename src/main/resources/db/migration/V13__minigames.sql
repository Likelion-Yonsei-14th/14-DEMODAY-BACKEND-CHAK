-- 미니게임: 찍신(서버가 정답을 쥐고 채점), 공부 유형 통계, 랭킹용 닉네임.
-- 찍신 한 판. 정답은 판을 만들 때 서버가 정하고 제출 전에는 내려주지 않는다. 회원이 제출하면 user_id가 붙어 랭킹에 오른다.
CREATE TABLE minigame_rounds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    game VARCHAR(20) NOT NULL,
    answer_key VARCHAR(40) NOT NULL,
    user_id BIGINT NULL,
    score INT NULL,
    created_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minigame_rounds_public_id UNIQUE (public_id),
    CONSTRAINT fk_minigame_rounds_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_minigame_rounds_game_submitted ON minigame_rounds (game, submitted_at);
CREATE INDEX idx_minigame_rounds_user_game_score ON minigame_rounds (user_id, game, score);
CREATE INDEX idx_minigame_rounds_created ON minigame_rounds (created_at);

-- 랭킹에 보이는 닉네임. 회원만 정하고, 정하지 않으면 이름 첫 글자만 보인다.
CREATE TABLE minigame_profiles (
    user_id BIGINT NOT NULL,
    nickname VARCHAR(12) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_minigame_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 나의 수험생 유형은? 결과. 회원은 한 줄(다시 하면 덮어씀), 비회원은 결과마다 한 줄.
CREATE TABLE study_type_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(3) NOT NULL,
    user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_study_type_results_user UNIQUE (user_id),
    CONSTRAINT fk_study_type_results_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_study_type_results_code ON study_type_results (code);
