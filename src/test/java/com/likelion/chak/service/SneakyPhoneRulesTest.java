package com.likelion.chak.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SneakyPhoneRulesTest {

    @Test
    void maxScoreFollowsTheStreakTiers() {
        // 0~1초 100점/초, 1~2초 150점/초, 2~3초 200점/초, 그 뒤 300점/초. 반올림 여유로 1점을 더 준다.
        assertThat(SneakyPhoneRules.maxBaseScore(0)).isEqualTo(1);
        assertThat(SneakyPhoneRules.maxBaseScore(500)).isEqualTo(51);
        assertThat(SneakyPhoneRules.maxBaseScore(1_500)).isEqualTo(100 + 75 + 1);
        assertThat(SneakyPhoneRules.maxBaseScore(3_000)).isEqualTo(450 + 1);
        assertThat(SneakyPhoneRules.maxBaseScore(30_000)).isEqualTo(450 + 8_100 + 1);
    }

    @Test
    void morePhoneTimeNeverLowersTheLimit() {
        int previous = 0;
        for (int ms = 0; ms <= 30_000; ms += 250) {
            int max = SneakyPhoneRules.maxBaseScore(ms);
            assertThat(max).isGreaterThanOrEqualTo(previous);
            previous = max;
        }
    }
}
