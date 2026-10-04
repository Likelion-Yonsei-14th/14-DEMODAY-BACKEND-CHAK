package com.likelion.chak.service;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class UnlockTimeCalculatorTest {

    private final UnlockTimeCalculator calculator = new UnlockTimeCalculator();

    @Test
    void dailyMessageBeforeCutoffUnlocksTheSameDay() {
        PersonalDesk desk = dailyDesk();
        Instant createdAt = Instant.parse("2026-10-04T12:59:00Z");

        Instant unlockAt = calculator.calculate(desk, createdAt);

        assertThat(unlockAt).isEqualTo(Instant.parse("2026-10-04T13:00:00Z"));
    }

    @Test
    void dailyMessageAfterCutoffUnlocksTheNextDay() {
        PersonalDesk desk = dailyDesk();
        Instant createdAt = Instant.parse("2026-10-04T13:01:00Z");

        Instant unlockAt = calculator.calculate(desk, createdAt);

        assertThat(unlockAt).isEqualTo(Instant.parse("2026-10-05T13:00:00Z"));
    }

    @Test
    void messageAfterCapsuleTimeUnlocksImmediately() {
        UserAccount owner = UserAccount.create("지수");
        PersonalDesk desk = PersonalDesk.createClaimed(
                owner,
                "지수",
                ReadModeType.TIME_CAPSULE,
                null,
                Instant.parse("2026-11-18T11:00:00Z"));
        Instant createdAt = Instant.parse("2026-11-18T12:00:00Z");

        assertThat(calculator.calculate(desk, createdAt)).isEqualTo(createdAt);
    }

    private PersonalDesk dailyDesk() {
        UserAccount owner = UserAccount.create("지수");
        return PersonalDesk.createClaimed(
                owner,
                "지수",
                ReadModeType.DAILY,
                LocalTime.of(22, 0),
                null);
    }
}
