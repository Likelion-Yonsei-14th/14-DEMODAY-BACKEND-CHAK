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
        UserAccount owner = UserAccount.createKakao("test-kakao-id-1", "지수", null, null);
        PersonalDesk desk = PersonalDesk.createClaimed(
                owner,
                "지수",
                ReadModeType.TIME_CAPSULE,
                null,
                Instant.parse("2026-11-18T11:00:00Z"));
        Instant createdAt = Instant.parse("2026-11-18T12:00:00Z");

        assertThat(calculator.calculate(desk, createdAt)).isEqualTo(createdAt);
    }

    @Test
    void dailyMessageSentExactlyAtTheCutoffUnlocksAtThatCutoff() {
        Instant createdAt = Instant.parse("2026-10-04T13:00:00Z");

        assertThat(calculator.calculate(dailyDesk(), createdAt)).isEqualTo(createdAt);
    }

    @Test
    void dailyCutoffIsKoreanTimeSoALateNightLetterWaitsForTheNextKoreanEvening() {
        // 2026-10-04 23:30 KST (14:30Z)는 같은 날 22시를 지났으므로 다음 날 22시 KST(13:00Z).
        Instant createdAt = Instant.parse("2026-10-04T14:30:00Z");

        assertThat(calculator.calculate(dailyDesk(), createdAt))
                .isEqualTo(Instant.parse("2026-10-05T13:00:00Z"));
    }

    @Test
    void dailyMessageSentJustAfterMidnightKoreanTimeUnlocksThatSameKoreanEvening() {
        // 2026-10-05 00:10 KST (UTC로는 전날 15:10)는 한국 날짜로 5일이므로 5일 22시 KST.
        Instant createdAt = Instant.parse("2026-10-04T15:10:00Z");

        assertThat(calculator.calculate(dailyDesk(), createdAt))
                .isEqualTo(Instant.parse("2026-10-05T13:00:00Z"));
    }

    @Test
    void messageBeforeCapsuleTimeWaitsForTheCapsuleTimeEvenIfSentJustBeforeIt() {
        PersonalDesk desk = capsuleDesk(Instant.parse("2026-11-18T11:00:00Z"));
        Instant createdAt = Instant.parse("2026-11-18T10:59:59Z");

        assertThat(calculator.calculate(desk, createdAt)).isEqualTo(Instant.parse("2026-11-18T11:00:00Z"));
    }

    @Test
    void messageSentExactlyAtTheCapsuleTimeIsAlreadyOpen() {
        Instant at = Instant.parse("2026-11-18T11:00:00Z");

        assertThat(calculator.calculate(capsuleDesk(at), at)).isEqualTo(at);
    }

    private PersonalDesk capsuleDesk(Instant unlockAt) {
        UserAccount owner = UserAccount.createKakao("test-kakao-id-3", "지수", null, null);
        return PersonalDesk.createClaimed(owner, "지수", ReadModeType.TIME_CAPSULE, null, unlockAt);
    }

    private PersonalDesk dailyDesk() {
        UserAccount owner = UserAccount.createKakao("test-kakao-id-2", "지수", null, null);
        return PersonalDesk.createClaimed(
                owner,
                "지수",
                ReadModeType.DAILY,
                LocalTime.of(22, 0),
                null);
    }
}
