package com.likelion.chak.service;

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
 * 로그인하지 않은 접속자의 "새 손님 기록" 생성 속도를 제한한다. 쿠키를 버리고 요청을 반복해 손님 행을 무한정 만드는
 * 남용을 막기 위한 것으로, 한 접속자(IP 해시)당 분당 30건, 전체 분당 600건. 이미 쿠키가 있는 손님에는 적용하지 않는다.
 * 접속자 해시는 1분이 지나면 버리고 원문 IP는 저장하지 않는다.
 */
@Component
public class GuestCreationLimiter {

    static final int PER_CLIENT_PER_MINUTE = 30;
    static final int GLOBAL_PER_MINUTE = 600;
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_CLIENTS = 5000;

    private final Clock clock;
    private final Map<String, Deque<Long>> perClient = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public GuestCreationLimiter() {
        this(Clock.systemUTC());
    }

    GuestCreationLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String ip) {
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
        Deque<Long> mine = perClient.computeIfAbsent(hash(ip), key -> new ArrayDeque<>());
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

    private static String hash(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
