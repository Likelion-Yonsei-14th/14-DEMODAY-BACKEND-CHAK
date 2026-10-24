package com.likelion.chak.alert;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 미니게임 남용 방지. 한 접속자(회원 번호 또는 IP 해시)가 정해진 시간 안에 할 수 있는 횟수를 제한한다.
 * 기록은 창이 지나면 버리고, 접속자 수가 너무 많아지면 새 접속자를 거절해 메모리를 지킨다.
 */
@Component
public class MinigameRateLimiter {

    public enum Action {
        /** 찍신 판 만들기: 시간당 60판. 맞힐 확률이 1천만 분의 1이라 반복 시도로 랭킹을 올리기 어렵다. */
        GUESS_GOD_ROUND(60, 3_600_000L),
        /** 한 판이 30초라 사람이 시간당 낼 수 있는 판 수보다 넉넉하게 100판. */
        SNEAKY_PHONE_ROUND(100, 3_600_000L),
        STUDY_TYPE_RESULT(20, 600_000L);

        final int limit;
        final long windowMs;

        Action(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }
    }

    private static final int MAX_KEYS = 20_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public MinigameRateLimiter() {
        this(Clock.systemUTC());
    }

    MinigameRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(Action action, String who) {
        long now = clock.millis();
        String key = action.name() + ":" + hash(who);
        if (hits.size() >= MAX_KEYS && !hits.containsKey(key)) {
            hits.values().removeIf(q -> q.isEmpty() || now - q.peekLast() >= 3_600_000L);
            if (hits.size() >= MAX_KEYS) {
                return false;
            }
        }
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
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
