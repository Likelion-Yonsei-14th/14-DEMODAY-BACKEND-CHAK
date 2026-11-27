package com.likelion.chak.alert;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorAlertServiceTest {

    /** 테스트에서 시간을 직접 흘려보내는 시계. */
    private static final class MutableClock extends Clock {
        private final AtomicLong now = new AtomicLong(1_000_000L);

        void advance(long ms) {
            now.addAndGet(ms);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }

        @Override
        public long millis() {
            return now.get();
        }
    }

    private final List<Integer> sentSuppressed = new ArrayList<>();
    private final List<String> sentFingerprints = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final AlertNotifier notifier = (message, suppressed) -> {
        sentFingerprints.add(message.fingerprint());
        sentSuppressed.add(suppressed);
    };
    private final ErrorAlertService service = new ErrorAlertService(notifier, Runnable::run, clock);

    private AlertMessage message(String fingerprint) {
        return AlertMessage.builder("서버", "제목", fingerprint, AlertMessage.Severity.ERROR).build();
    }

    @Test
    void sendsFirstOccurrenceAndGroupsRepeatsWithinFiveMinutes() {
        service.alert(message("a"));
        service.alert(message("a"));
        service.alert(message("a"));
        assertThat(sentFingerprints).containsExactly("a");

        clock.advance(ErrorAlertService.WINDOW_MS + 1);
        service.alert(message("a"));
        assertThat(sentFingerprints).containsExactly("a", "a");
        assertThat(sentSuppressed).containsExactly(0, 2);
    }

    @Test
    void differentErrorsAreNotGroupedTogether() {
        service.alert(message("a"));
        service.alert(message("b"));
        assertThat(sentFingerprints).containsExactly("a", "b");
    }

    @Test
    void capsTotalAlertsPerMinute() {
        for (int i = 0; i < ErrorAlertService.MAX_PER_MINUTE + 15; i++) {
            service.alert(message("fp-" + i));
        }
        assertThat(sentFingerprints).hasSize(ErrorAlertService.MAX_PER_MINUTE);

        clock.advance(61_000);
        service.alert(message("after"));
        assertThat(sentFingerprints).hasSize(ErrorAlertService.MAX_PER_MINUTE + 1);
    }

    @Test
    void floodsOfUserReportedErrorsCannotUseUpTheServerAlertBudget() {
        for (int i = 0; i < 100; i++) {
            service.alert(AlertMessage.builder("프론트", "제보", "client|" + i, AlertMessage.Severity.ERROR)
                    .userReported().build());
        }
        assertThat(sentFingerprints).hasSize(ErrorAlertService.MAX_USER_REPORTED_PER_MINUTE);

        service.alert(message("server|payment-failed"));
        assertThat(sentFingerprints).contains("server|payment-failed");
    }

    @Test
    void logNotifierEscapesNewlinesSoUserTextCannotForgeLogLines() {
        assertThat(LogAlertNotifier.oneLine("a\n2026-11-27 INFO forged\r\nb")).doesNotContain("\n").doesNotContain("\r")
                .isEqualTo("a\\n2026-11-27 INFO forged\\r\\nb");
    }

    @Test
    void aFailingNotifierNeverBreaksTheCaller() {
        ErrorAlertService failing = new ErrorAlertService(
                (m, s) -> {
                    throw new IllegalStateException("discord down");
                }, Runnable::run, clock);
        failing.alert(message("x"));
    }
}
