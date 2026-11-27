package com.likelion.chak.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그인 없이 쓸 수 있는 쓰기 요청(응원 보내기, 신고, 주문)의 남용 방지.
 * 같은 접속자(IP 해시·비회원 쿠키·회원 번호)가 정해진 시간 안에 할 수 있는 횟수와, 한 책상이 받을 수 있는 응원 수를 제한한다.
 * IP는 요청 헤더로 바꿀 수 있어서 한 책상에 쏟아지는 응원을 막는 책상별 상한을 함께 둔다.
 * 기록은 창이 지나면 버린다. 키 표는 종류별로 따로 두고, 가득 차면 가장 오래 쓰이지 않은 키를 버린다(LRU).
 * 예전처럼 새 키를 거절하면 위조한 키로 표를 채운 사람 하나 때문에 처음 오는 모든 사람이 막혔다.
 */
@Component
public class PublicWriteLimiter {

    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 86_400_000L;
    /** 종류별 키 표 크기. 넘치면 가장 오래 쓰이지 않은 키부터 버린다. */
    static final int MAX_KEYS_PER_ACTION = 50_000;

    public enum Action {
        /** 비회원 응원: 같은 IP에서 시간당 30건. */
        GUEST_MESSAGE_IP(30, HOUR_MS),
        /** 비회원 응원: 같은 비회원 쿠키로 시간당 20건. */
        GUEST_MESSAGE_ID(20, HOUR_MS),
        USER_MESSAGE(60, HOUR_MS),
        /** 한 책상이 한 시간에 받는 응원. 수능 직전에 친구가 몰려도 넉넉한 값이다. */
        DESK_MESSAGE_HOUR(300, HOUR_MS),
        DESK_MESSAGE_DAY(1_500, DAY_MS),
        REPORT(30, HOUR_MS),
        /** 편지 반응(하트 등): 같은 접속자가 시간당 300번. 공개 목록을 훑으며 누르는 사람도 넉넉하다. */
        REACTION(300, HOUR_MS),
        /** 광고 보고 해제: 같은 접속자가 시간당 60번. */
        UNLOCK(60, HOUR_MS),
        ORDER(20, HOUR_MS),
        /** 프로필 이름·사진 변경: 회원 한 명이 시간당 20번. */
        PROFILE_UPDATE(20, HOUR_MS),
        /** 롤링페이퍼 반 만들기: 같은 접속자가 시간당 10번. */
        ROLLING_PAPER_CREATE(10, HOUR_MS),
        /** 롤링페이퍼 쓰기(참여·한마디·칠판·설정): 같은 접속자가 시간당 200번. 한 반 친구 수십 명에게 쓰고 고쳐도 넉넉하다. */
        ROLLING_PAPER_WRITE(200, HOUR_MS);

        final int limit;
        final long windowMs;

        Action(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }
    }

    private final Clock clock;
    private final int scale;
    private final Map<Action, Map<String, Deque<Long>>> hits = new EnumMap<>(Action.class);

    @org.springframework.beans.factory.annotation.Autowired
    public PublicWriteLimiter(@Value("${chak.limit.scale:1}") int scale) {
        this(Clock.systemUTC(), scale);
    }

    PublicWriteLimiter(Clock clock, int scale) {
        this.clock = clock;
        this.scale = Math.max(1, scale);
    }

    /** 허용되면 횟수를 올리고 true. */
    public synchronized boolean tryAcquire(Action action, String who) {
        Deque<Long> queue = queue(action, who, true);
        if (queue == null || queue.size() >= action.limit * scale) {
            return false;
        }
        queue.addLast(clock.millis());
        return true;
    }

    /** 횟수를 올리지 않고 아직 여유가 있는지만 본다. */
    public synchronized boolean allows(Action action, String who) {
        Deque<Long> queue = queue(action, who, false);
        return queue == null || queue.size() < action.limit * scale;
    }

    /** 요청이 성공했을 때 횟수를 올린다(책상별 상한처럼 실패한 요청은 세지 않을 때). */
    public synchronized void record(Action action, String who) {
        Deque<Long> queue = queue(action, who, true);
        if (queue != null) {
            queue.addLast(clock.millis());
        }
    }

    /** 키별 기록을 가져온다. create=false이고 기록이 없으면 null. */
    private Deque<Long> queue(Action action, String who, boolean create) {
        long now = clock.millis();
        Map<String, Deque<Long>> table = hits.computeIfAbsent(action, a -> lruTable(MAX_KEYS_PER_ACTION));
        String key = hash(who);
        Deque<Long> queue = table.get(key);
        if (queue == null) {
            if (!create) {
                return null;
            }
            queue = new ArrayDeque<>();
            table.put(key, queue);
        }
        while (!queue.isEmpty() && now - queue.peekFirst() >= action.windowMs) {
            queue.pollFirst();
        }
        return queue;
    }

    /** 접근 순서로 정렬하고, 가득 차면 가장 오래 쓰이지 않은 키를 버리는 표. */
    static <V> Map<String, V> lruTable(int maxKeys) {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > maxKeys;
            }
        };
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
