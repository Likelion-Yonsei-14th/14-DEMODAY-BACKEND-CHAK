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
 * 프론트 오류 접수 API의 남용을 막는 메모리 기반 제한. 한 접속자당 분당 10건, 전체 분당 120건.
 * 접속자는 IP+브라우저 정보의 해시로만 구분하며 원문은 저장하지 않고, 해시도 1분이 지나면 버린다.
 */
@Component
public class ClientErrorRateLimiter {

    static final int PER_CLIENT_PER_MINUTE = 10;
    static final int GLOBAL_PER_MINUTE = 120;
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_CLIENTS = 5000;

    private final Clock clock;
    private final Map<String, Deque<Long>> perClient = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public ClientErrorRateLimiter() {
        this(Clock.systemUTC());
    }

    ClientErrorRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String ip, String userAgent) {
        long now = clock.millis();
        prune(global, now);
        if (global.size() >= GLOBAL_PER_MINUTE) {
            return false;
        }
        if (perClient.size() >= MAX_CLIENTS) {
            perClient.values().removeIf(q -> {
                prune(q, now);
                return q.isEmpty();
            });
            if (perClient.size() >= MAX_CLIENTS) {
                return false;
            }
        }
        Deque<Long> mine = perClient.computeIfAbsent(hash(ip, userAgent), k -> new ArrayDeque<>());
        prune(mine, now);
        if (mine.size() >= PER_CLIENT_PER_MINUTE) {
            return false;
        }
        mine.addLast(now);
        global.addLast(now);
        return true;
    }

    private static void prune(Deque<Long> queue, long now) {
        while (!queue.isEmpty() && now - queue.peekFirst() >= WINDOW_MS) {
            queue.pollFirst();
        }
    }

    private static String hash(String ip, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((String.valueOf(ip) + "|" + String.valueOf(userAgent)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
