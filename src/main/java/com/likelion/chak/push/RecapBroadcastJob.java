package com.likelion.chak.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 수능이 끝난 날 저녁 6시, 알림을 켜 둔 모든 브라우저에 "수고 많았어요, 리캡이 열렸어요" 알림을 한 번 보낸다.
 * 보냈다는 기록을 먼저 남기고 보내서 서버가 다시 떠도 두 번 가지 않는다. 배포가 늦어 2시간이 지나면 보내지 않는다.
 */
@Component
@ConditionalOnProperty(name = "chak.recap-broadcast.enabled", havingValue = "true", matchIfMissing = true)
public class RecapBroadcastJob {

    private static final Logger log = LoggerFactory.getLogger(RecapBroadcastJob.class);
    static final String KEY = "recap-2026";
    static final Instant SEND_AT = Instant.parse("2026-11-19T09:00:00Z");          // 2026-11-19 18:00 KST
    static final Instant GIVE_UP_AT = SEND_AT.plusSeconds(2 * 60 * 60);

    private final PushService pushService;
    private final PushSubscriptionRepository repository;
    private final PushNotifier notifier;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public RecapBroadcastJob(PushService pushService, PushSubscriptionRepository repository, PushNotifier notifier,
                             JdbcTemplate jdbc) {
        this(pushService, repository, notifier, jdbc, Clock.systemUTC());
    }

    RecapBroadcastJob(PushService pushService, PushSubscriptionRepository repository, PushNotifier notifier,
                      JdbcTemplate jdbc, Clock clock) {
        this.pushService = pushService;
        this.repository = repository;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() {
        try {
            run();
        } catch (RuntimeException e) {
            log.warn("Recap broadcast failed: {}", e.toString());
        }
    }

    /** 보냈으면 보낸 구독 수, 보낼 때가 아니거나 이미 보냈으면 -1. */
    int run() {
        Instant now = clock.instant();
        if (!pushService.isEnabled() || now.isBefore(SEND_AT) || !now.isBefore(GIVE_UP_AT)) {
            return -1;
        }
        try {
            jdbc.update("INSERT INTO push_broadcasts (broadcast_key, sent_at) VALUES (?, ?)", KEY, Timestamp.from(now));
        } catch (DataIntegrityViolationException alreadySent) {
            return -1;
        }
        List<Long> ids = repository.findAll().stream().map(PushSubscription::getId).toList();
        byte[] payload = payload();
        for (Long id : ids) {
            notifier.deliver(id, payload);
        }
        log.info("Recap broadcast sent to {} subscriptions", ids.size());
        return ids.size();
    }

    static byte[] payload() {
        String json = "{\"title\":\"수능, 정말 수고 많았어요\",\"body\":\"그동안 받은 응원을 나의 수능 리캡에 모아 뒀어요. 지금 열어 보세요\","
                + "\"url\":\"/recap\",\"tag\":\"recap\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
