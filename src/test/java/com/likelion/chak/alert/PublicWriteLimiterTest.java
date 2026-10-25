package com.likelion.chak.alert;

import com.likelion.chak.alert.PublicWriteLimiter.Action;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PublicWriteLimiterTest {

    private final AtomicLong now = new AtomicLong(10_000_000L);
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    };

    @Test
    void limitsEachKeyAndActionSeparatelyAndFreesTheWindowLater() {
        var limiter = new PublicWriteLimiter(clock, 1);
        for (int i = 0; i < 30; i++) assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "2.2.2.2")).isTrue();        // 다른 접속자
        assertThat(limiter.tryAcquire(Action.REPORT, "1.1.1.1")).isTrue();                  // 다른 종류

        now.addAndGet(3_600_001);
        assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "1.1.1.1")).isTrue();
    }

    @Test
    void deskCapOnlyCountsWhatWasRecordedSoFailedRequestsDoNotFillIt() {
        var limiter = new PublicWriteLimiter(clock, 1);
        for (int i = 0; i < 1_000; i++) assertThat(limiter.allows(Action.DESK_MESSAGE_HOUR, "desk-a")).isTrue();   // 보기만 해서는 안 찬다

        for (int i = 0; i < 300; i++) limiter.record(Action.DESK_MESSAGE_HOUR, "desk-a");
        assertThat(limiter.allows(Action.DESK_MESSAGE_HOUR, "desk-a")).isFalse();
        assertThat(limiter.allows(Action.DESK_MESSAGE_HOUR, "desk-b")).isTrue();
    }

    @Test
    void scaleRaisesEveryLimit() {
        var limiter = new PublicWriteLimiter(clock, 3);
        for (int i = 0; i < 90; i++) assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "ip")).isTrue();
        assertThat(limiter.tryAcquire(Action.GUEST_MESSAGE_IP, "ip")).isFalse();
    }
}
