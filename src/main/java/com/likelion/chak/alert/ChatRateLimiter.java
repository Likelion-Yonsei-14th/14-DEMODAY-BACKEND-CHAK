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
 * 수능 도우미 챗봇의 비용 보호: 한 접속자(IP 해시)당 1시간 20번, 전체 1시간 1,500번.
 * 한 번에 라이너 검색 1회와 짧은 글 생성 2회를 쓰므로 전체 상한이 하루 예산을 넘지 않게 잡았다.
 */
@Component
public class ChatRateLimiter {

    static final int PER_CLIENT_PER_HOUR = 20;
    static final int GLOBAL_PER_HOUR = 1500;
    private static final long WINDOW_MS = 3_600_000L;
    private static final int MAX_CLIENTS = 20000;

    private final Clock clock;
    private final Map<String, Deque<Long>> perClient = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public ChatRateLimiter() {
        this(Clock.systemUTC());
    }

    ChatRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String client) {
        long now = clock.millis();
        prune(global, now);
        if (global.size() >= GLOBAL_PER_HOUR) return false;
        if (perClient.size() >= MAX_CLIENTS) {
            perClient.values().removeIf(q -> {
                prune(q, now);
                return q.isEmpty();
            });
            if (perClient.size() >= MAX_CLIENTS) return false;
        }
        Deque<Long> mine = perClient.computeIfAbsent(hash(client), key -> new ArrayDeque<>());
        prune(mine, now);
        if (mine.size() >= PER_CLIENT_PER_HOUR) return false;
        mine.addLast(now);
        global.addLast(now);
        return true;
    }

    private static void prune(Deque<Long> queue, long now) {
        while (!queue.isEmpty() && now - queue.peekFirst() >= WINDOW_MS) queue.pollFirst();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
