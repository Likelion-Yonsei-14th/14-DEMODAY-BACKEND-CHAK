-- 수능 여론: 하루 한두 개씩 여는 짧은 투표. 로그인 없이 한 사람(회원 또는 손님 쿠키)이 한 번씩 고른다.
-- 시각은 UTC로 저장한다(한국 시각 = UTC + 9시간).
CREATE TABLE polls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(60) NOT NULL,
    tag VARCHAR(30) NOT NULL,
    question VARCHAR(120) NOT NULL,
    opens_at DATETIME(6) NOT NULL,
    closes_at DATETIME(6) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_polls_slug UNIQUE (slug)
);

CREATE INDEX idx_polls_opens ON polls (opens_at);

CREATE TABLE poll_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poll_id BIGINT NOT NULL,
    label VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_poll_options_poll FOREIGN KEY (poll_id) REFERENCES polls (id)
);

CREATE INDEX idx_poll_options_poll ON poll_options (poll_id);

-- voter_key: 회원이면 'u:회원번호', 손님이면 'g:손님번호'. 투표당 한 번만 들어간다.
-- region: 투표할 때의 접속 지역(시·도 이름). IP는 저장하지 않는다.
CREATE TABLE poll_votes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poll_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    voter_key VARCHAR(40) NOT NULL,
    region VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_poll_votes_voter UNIQUE (poll_id, voter_key),
    CONSTRAINT fk_poll_votes_poll FOREIGN KEY (poll_id) REFERENCES polls (id),
    CONSTRAINT fk_poll_votes_option FOREIGN KEY (option_id) REFERENCES poll_options (id)
);

CREATE INDEX idx_poll_votes_poll_option ON poll_votes (poll_id, option_id);

-- ---- 처음 여는 질문들 ----
-- D-2 (11/17 00:00 ~ 11/19 08:40 KST)
INSERT INTO polls (slug, tag, question, opens_at, closes_at, sort_order, created_at) VALUES
('2026-d2-worry-subject', 'D-2', '수능에서 제일 걱정되는 과목은?', '2026-11-16 15:00:00', '2026-11-18 23:40:00', 1, CURRENT_TIMESTAMP),
('2026-d2-bedtime', 'D-2', '수능 전날, 몇 시에 잘 거야?', '2026-11-16 15:00:00', '2026-11-18 23:40:00', 2, CURRENT_TIMESTAMP),
-- D-1 (11/18 00:00 ~ 11/19 08:40 KST)
('2026-d1-lunchbox', 'D-1', '수능 도시락, 뭐 싸 가?', '2026-11-17 15:00:00', '2026-11-18 23:40:00', 1, CURRENT_TIMESTAMP),
('2026-d1-first-thing', 'D-1', '수능 끝나고 제일 먼저 할 일은?', '2026-11-17 15:00:00', '2026-11-18 23:40:00', 2, CURRENT_TIMESTAMP),
-- 수능 당일 (11/19 16:40 KST, 4교시가 끝난 뒤 열림)
('2026-exam-korean', '수능 당일', '국어, 체감 난이도는?', '2026-11-19 07:40:00', NULL, 1, CURRENT_TIMESTAMP),
('2026-exam-math', '수능 당일', '수학, 체감 난이도는?', '2026-11-19 07:40:00', NULL, 2, CURRENT_TIMESTAMP),
('2026-exam-english', '수능 당일', '영어, 체감 난이도는?', '2026-11-19 07:40:00', NULL, 3, CURRENT_TIMESTAMP),
('2026-exam-inquiry', '수능 당일', '탐구, 체감 난이도는?', '2026-11-19 07:40:00', NULL, 4, CURRENT_TIMESTAMP),
('2026-exam-hardest', '수능 당일', '오늘 제일 어려웠던 과목은?', '2026-11-19 07:40:00', NULL, 5, CURRENT_TIMESTAMP),
('2026-exam-mood', '수능 당일', '시험 끝난 지금 기분은?', '2026-11-19 07:40:00', NULL, 6, CURRENT_TIMESTAMP),
('2026-exam-dinner', '수능 당일', '오늘 저녁 뭐 먹어?', '2026-11-19 07:40:00', NULL, 7, CURRENT_TIMESTAMP),
-- 수능 다음 날부터 (11/20 00:00 KST)
('2026-after-this-week', '수능 끝', '수능 끝, 이번 주에 꼭 할 것은?', '2026-11-19 15:00:00', NULL, 1, CURRENT_TIMESTAMP),
('2026-after-hair', '수능 끝', '수능 끝나고 머리, 어떻게 할 거야?', '2026-11-19 15:00:00', NULL, 2, CURRENT_TIMESTAMP);

INSERT INTO poll_options (poll_id, label, sort_order)
SELECT p.id, o.label, o.sort_order FROM polls p JOIN (
    SELECT '2026-d2-worry-subject' AS slug, '국어' AS label, 1 AS sort_order
    UNION ALL SELECT '2026-d2-worry-subject', '수학', 2
    UNION ALL SELECT '2026-d2-worry-subject', '영어', 3
    UNION ALL SELECT '2026-d2-worry-subject', '탐구', 4
    UNION ALL SELECT '2026-d2-worry-subject', '다 괜찮아', 5
    UNION ALL SELECT '2026-d2-bedtime', '10시 전', 1
    UNION ALL SELECT '2026-d2-bedtime', '10~11시', 2
    UNION ALL SELECT '2026-d2-bedtime', '11~12시', 3
    UNION ALL SELECT '2026-d2-bedtime', '12시 넘어서', 4
    UNION ALL SELECT '2026-d2-bedtime', '못 잘 것 같아', 5
    UNION ALL SELECT '2026-d1-lunchbox', '밥이랑 반찬', 1
    UNION ALL SELECT '2026-d1-lunchbox', '죽', 2
    UNION ALL SELECT '2026-d1-lunchbox', '김밥·주먹밥', 3
    UNION ALL SELECT '2026-d1-lunchbox', '빵·간단하게', 4
    UNION ALL SELECT '2026-d1-lunchbox', '아직 몰라', 5
    UNION ALL SELECT '2026-d1-first-thing', '폰 보기', 1
    UNION ALL SELECT '2026-d1-first-thing', '맛있는 거 먹기', 2
    UNION ALL SELECT '2026-d1-first-thing', '자기', 3
    UNION ALL SELECT '2026-d1-first-thing', '가채점', 4
    UNION ALL SELECT '2026-d1-first-thing', '친구 만나기', 5
    UNION ALL SELECT '2026-exam-korean', '불수능', 1
    UNION ALL SELECT '2026-exam-korean', '어려웠어', 2
    UNION ALL SELECT '2026-exam-korean', '평이했어', 3
    UNION ALL SELECT '2026-exam-korean', '쉬웠어', 4
    UNION ALL SELECT '2026-exam-korean', '물수능', 5
    UNION ALL SELECT '2026-exam-math', '불수능', 1
    UNION ALL SELECT '2026-exam-math', '어려웠어', 2
    UNION ALL SELECT '2026-exam-math', '평이했어', 3
    UNION ALL SELECT '2026-exam-math', '쉬웠어', 4
    UNION ALL SELECT '2026-exam-math', '물수능', 5
    UNION ALL SELECT '2026-exam-english', '불수능', 1
    UNION ALL SELECT '2026-exam-english', '어려웠어', 2
    UNION ALL SELECT '2026-exam-english', '평이했어', 3
    UNION ALL SELECT '2026-exam-english', '쉬웠어', 4
    UNION ALL SELECT '2026-exam-english', '물수능', 5
    UNION ALL SELECT '2026-exam-inquiry', '불수능', 1
    UNION ALL SELECT '2026-exam-inquiry', '어려웠어', 2
    UNION ALL SELECT '2026-exam-inquiry', '평이했어', 3
    UNION ALL SELECT '2026-exam-inquiry', '쉬웠어', 4
    UNION ALL SELECT '2026-exam-inquiry', '물수능', 5
    UNION ALL SELECT '2026-exam-hardest', '국어', 1
    UNION ALL SELECT '2026-exam-hardest', '수학', 2
    UNION ALL SELECT '2026-exam-hardest', '영어', 3
    UNION ALL SELECT '2026-exam-hardest', '한국사', 4
    UNION ALL SELECT '2026-exam-hardest', '탐구', 5
    UNION ALL SELECT '2026-exam-mood', '후련해', 1
    UNION ALL SELECT '2026-exam-mood', '아쉬워', 2
    UNION ALL SELECT '2026-exam-mood', '멍해', 3
    UNION ALL SELECT '2026-exam-mood', '배고파', 4
    UNION ALL SELECT '2026-exam-mood', '잠 와', 5
    UNION ALL SELECT '2026-exam-dinner', '고기', 1
    UNION ALL SELECT '2026-exam-dinner', '치킨·피자', 2
    UNION ALL SELECT '2026-exam-dinner', '마라탕·떡볶이', 3
    UNION ALL SELECT '2026-exam-dinner', '집밥', 4
    UNION ALL SELECT '2026-exam-dinner', '아직 몰라', 5
    UNION ALL SELECT '2026-after-this-week', '염색·헤어', 1
    UNION ALL SELECT '2026-after-this-week', '렌즈·안경', 2
    UNION ALL SELECT '2026-after-this-week', '운전면허', 3
    UNION ALL SELECT '2026-after-this-week', '여행', 4
    UNION ALL SELECT '2026-after-this-week', '알바', 5
    UNION ALL SELECT '2026-after-hair', '탈색·밝은 염색', 1
    UNION ALL SELECT '2026-after-hair', '자연스러운 염색', 2
    UNION ALL SELECT '2026-after-hair', '펌', 3
    UNION ALL SELECT '2026-after-hair', '커트만', 4
    UNION ALL SELECT '2026-after-hair', '그대로 둘래', 5
) o ON o.slug = p.slug;
