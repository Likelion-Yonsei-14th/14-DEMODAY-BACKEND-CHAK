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

/** 제휴 신청서의 파일 올리기 남용 방지: 한 접속자(IP 해시)당 1시간 20번, 전체 1시간 300번. 해시는 1시간이 지나면 버린다. */
@Component
public class UploadRateLimiter {

    static final int PER_CLIENT_PER_HOUR = 20;
    static final int GLOBAL_PER_HOUR = 300;
    private static final long WINDOW_MS = 3_600_000L;
    private static final int MAX_CLIENTS = 5000;

    private final Clock clock;
    private final Map<String, Deque<Long>> perClient = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public UploadRateLimiter() {
        this(Clock.systemUTC());
    }

    UploadRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String ip) {
        long now = clock.millis();
        prune(global, now);
        if (global.size() >= GLOBAL_PER_HOUR) {
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
        Deque<Long> mine = perClient.computeIfAbsent(hash(ip), key -> new ArrayDeque<>());
        prune(mine, now);
        if (mine.size() >= PER_CLIENT_PER_HOUR) {
            return false;
        }
        mine.addLast(now);
        global.addLast(now);
        return true;
    }

    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.removeFirst();
        }
    }

    private static String hash(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
