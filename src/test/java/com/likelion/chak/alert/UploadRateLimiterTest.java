package com.likelion.chak.alert;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRateLimiterTest {

    private static final long MB = 1024L * 1024;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void capsTheBytesOneClientCanBeHandedPerHour() {
        var limiter = new UploadRateLimiter(clock);
        for (int i = 0; i < 10; i++) assertThat(limiter.tryAcquire("1.1.1.1", 20 * MB)).isTrue();   // 200MB
        assertThat(limiter.tryAcquire("1.1.1.1", 1)).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2", 20 * MB)).isTrue();
    }

    @Test
    void capsTheTotalBytesAcrossClientsPerHour() {
        var limiter = new UploadRateLimiter(clock);
        int granted = 0;
        for (int i = 0; i < 200; i++) {
            if (limiter.tryAcquire("10.0.0." + i, 20 * MB)) granted++;
        }
        assertThat(granted * 20 * MB).isLessThanOrEqualTo(UploadRateLimiter.GLOBAL_BYTES_PER_HOUR);
        assertThat(granted).isEqualTo((int) (UploadRateLimiter.GLOBAL_BYTES_PER_HOUR / (20 * MB)));
    }
}
