package com.likelion.chak.alert;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 미니게임 남용 방지. 한 접속자(회원 번호 또는 IP 해시)가 정해진 시간 안에 할 수 있는 횟수를 제한한다.
 * 기록은 창이 지나면 버린다. 키 표는 종류별로 따로 두고, 가득 차면 가장 오래 쓰이지 않은 키를 버린다(LRU).
 * 예전처럼 새 접속자를 거절하면 위조한 키로 표를 채운 사람 하나 때문에 처음 오는 모든 사람이 막혔다.
 */
@Component
public class MinigameRateLimiter {

    public enum Action {
        /** 찍신 판 만들기: 시간당 60판. 맞힐 확률이 1천만 분의 1이라 반복 시도로 랭킹을 올리기 어렵다. */
        GUESS_GOD_ROUND(60, 3_600_000L),
        /** 한 판이 30초라 사람이 시간당 낼 수 있는 판 수보다 넉넉하게 100판. */
        SNEAKY_PHONE_ROUND(100, 3_600_000L),
        /** 수능 블라스트는 한 판이 몇 분 걸려 사람이 시간당 낼 수 있는 판 수보다 넉넉하게 60판. */
        DESK_BLAST_ROUND(60, 3_600_000L),
        STUDY_TYPE_RESULT(20, 600_000L),
        /** 코스 응답은 한 판에 한두 번 보내고, 학교 공용 와이파이처럼 한 주소에 여럿이 있을 수 있어 넉넉하게 시간당 60건. */
        D1_COURSE_RESPONSE(60, 3_600_000L),
        /** 한 판에 시작과 끝 두 번만 보내지만 학교 공용 와이파이를 감안해 시간당 120건. */
        EXAM_CAREER_RUN(120, 3_600_000L),
        /**
         * 찍신 회원 제출: 회원 한 명이 시간당 60판(판 만들기 상한과 같다). 판은 비회원으로 만들고 제출만 로그인해서
         * 할 수 있어, 판 만들기 상한만으로는 한 계정이 랭킹용 시도를 무한정 늘릴 수 있었다.
         */
        GUESS_GOD_MEMBER_SUBMIT(60, 3_600_000L),
        /** 랭킹 조회: 같은 접속자가 시간당 600번. 결과 화면마다 한두 번 부르므로 학교 공용 와이파이도 넉넉하다. */
        LEADERBOARD(600, 3_600_000L),
        /** 수능 여론 투표. 한 사람이 하루 열 개 안팎을 고르지만 학교·학원 공용 와이파이를 감안해 시간당 300건. */
        POLL_VOTE(300, 3_600_000L),
        /**
         * 한 투표에 같은 접속자가 던지는 표: 시간당 60표. 비회원 쿠키는 공짜라 쿠키만 바꿔 한 투표를 몰아가는 것을 막는다.
         * 한 반·학원이 같은 와이파이로 투표해도 넉넉한 값이다.
         */
        POLL_VOTE_PER_POLL(60, 3_600_000L);

        final int limit;
        final long windowMs;

        Action(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }
    }

    /** 종류별 키 표 크기. 넘치면 가장 오래 쓰이지 않은 키부터 버린다. */
    static final int MAX_KEYS_PER_ACTION = 20_000;

    private final Clock clock;
    private final Map<Action, Map<String, Deque<Long>>> hits = new EnumMap<>(Action.class);

    public MinigameRateLimiter() {
        this(Clock.systemUTC());
    }

    MinigameRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(Action action, String who) {
        long now = clock.millis();
        Map<String, Deque<Long>> table =
                hits.computeIfAbsent(action, a -> PublicWriteLimiter.lruTable(MAX_KEYS_PER_ACTION));
        Deque<Long> queue = table.computeIfAbsent(hash(who), k -> new ArrayDeque<>());
        while (!queue.isEmpty() && now - queue.peekFirst() >= action.windowMs) {
            queue.pollFirst();
        }
        if (queue.size() >= action.limit) {
            return false;
        }
        queue.addLast(now);
        return true;
    }

    private static String hash(String who) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(who).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
