package com.likelion.chak.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCreationLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    };

    @Test
    void limitsOneClientPerMinuteAndFreesTheWindowAfterAMinute() {
        var limiter = new GuestCreationLimiter(clock);
        for (int i = 0; i < GuestCreationLimiter.PER_CLIENT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();      // 다른 접속자는 영향 없음

        now.addAndGet(60_001);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }

    @Test
    void capsTheWholeServerEvenWhenEveryRequestComesFromADifferentAddress() {
        var limiter = new GuestCreationLimiter(clock);
        int accepted = 0;
        for (int i = 0; i < GuestCreationLimiter.GLOBAL_PER_MINUTE + 50; i++) {
            if (limiter.tryAcquire("10.0." + (i / 250) + "." + (i % 250))) accepted++;
        }
        assertThat(accepted).isEqualTo(GuestCreationLimiter.GLOBAL_PER_MINUTE);
    }
}
