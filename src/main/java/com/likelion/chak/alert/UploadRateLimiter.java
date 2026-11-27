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
 * 제휴 신청서의 파일 올리기 남용 방지: 한 접속자(IP 해시)당 1시간 20번·200MB, 전체 1시간 300번·2GB.
 * 바이트 한도는 발급한 서명 주소가 허용하는 크기(서명에 들어간 Content-Length)로 센다. 해시는 1시간이 지나면 버린다.
 */
@Component
public class UploadRateLimiter {

    static final int PER_CLIENT_PER_HOUR = 20;
    static final int GLOBAL_PER_HOUR = 300;
    static final long BYTES_PER_CLIENT_PER_HOUR = 200L * 1024 * 1024;
    static final long GLOBAL_BYTES_PER_HOUR = 2L * 1024 * 1024 * 1024;
    private static final long WINDOW_MS = 3_600_000L;
    private static final int MAX_CLIENTS = 5000;

    private final Clock clock;
    private final Map<String, Deque<Long>> perClient = new HashMap<>();
    private final Map<String, Deque<long[]>> bytesPerClient = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();
    private final Deque<long[]> globalBytes = new ArrayDeque<>();

    public UploadRateLimiter() {
        this(Clock.systemUTC());
    }

    UploadRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String ip) {
        return tryAcquire(ip, 0);
    }

    /** 횟수와 함께 이번에 허용할 바이트 수도 센다. */
    public synchronized boolean tryAcquire(String ip, long bytes) {
        long now = clock.millis();
        prune(global, now);
        pruneBytes(globalBytes, now);
        if (global.size() >= GLOBAL_PER_HOUR || sum(globalBytes) + bytes > GLOBAL_BYTES_PER_HOUR) {
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
        String key = hash(ip);
        Deque<Long> mine = perClient.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(mine, now);
        Deque<long[]> myBytes = bytesPerClient.computeIfAbsent(key, k -> new ArrayDeque<>());
        pruneBytes(myBytes, now);
        if (mine.size() >= PER_CLIENT_PER_HOUR || sum(myBytes) + bytes > BYTES_PER_CLIENT_PER_HOUR) {
            return false;
        }
        mine.addLast(now);
        global.addLast(now);
        if (bytes > 0) {
            myBytes.addLast(new long[] {now, bytes});
            globalBytes.addLast(new long[] {now, bytes});
        }
        if (bytesPerClient.size() > MAX_CLIENTS) {
            bytesPerClient.values().removeIf(q -> {
                pruneBytes(q, now);
                return q.isEmpty();
            });
        }
        return true;
    }

    private static void pruneBytes(Deque<long[]> entries, long now) {
        while (!entries.isEmpty() && now - entries.peekFirst()[0] >= WINDOW_MS) {
            entries.removeFirst();
        }
    }

    private static long sum(Deque<long[]> entries) {
        long total = 0;
        for (long[] entry : entries) {
            total += entry[1];
        }
        return total;
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
