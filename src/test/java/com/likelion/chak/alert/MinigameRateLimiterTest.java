package com.likelion.chak.alert;

import com.likelion.chak.alert.MinigameRateLimiter.Action;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MinigameRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    };

    @Test
    void limitsPerClientAndPerActionAndFreesTheWindowLater() {
        var limiter = new MinigameRateLimiter(clock);
        for (int i = 0; i < 20; i++) assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isTrue();
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isFalse();
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "b")).isTrue();        // 다른 접속자
        assertThat(limiter.tryAcquire(Action.GUESS_GOD_ROUND, "a")).isTrue();          // 다른 종류

        now.addAndGet(600_001);
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isTrue();
    }
}
