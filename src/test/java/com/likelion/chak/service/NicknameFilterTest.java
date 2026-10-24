package com.likelion.chak.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameFilterTest {

    private final NicknameFilter filter = new NicknameFilter();

    @Test
    void blocksPlainBadWordsOfEveryKind() {
        for (String bad : new String[]{"시발", "병신아", "개새끼", "섹스왕", "한남충", "자살할래", "마약상", "fuck", "Shit", "운영자", "admin", "Hitler"}) {
            assertThat(filter.isAllowed(bad)).as(bad).isFalse();
        }
    }

    @Test
    void blocksWordsHiddenInsideOtherTextOrWithFillers() {
        for (String bad : new String[]{"착시발착", "시 발", "시1발", "시_발", "시.발", "f.u.c.k", "sh1t", "s3x", "B1TCH", "ＦＵＣＫ", "관 리 자"}) {
            assertThat(filter.isAllowed(bad)).as(bad).isFalse();
        }
    }

    @Test
    void allowsOrdinaryNicknamesAndStudyWords() {
        for (String ok : new String[]{"찍신왕", "수능대박", "합격기원", "시발점", "출발", "새끼손가락", "Essex", "grape", "crisis", "수학공식", "고3 화이팅", "민준123", "소년", "chak_fan"}) {
            assertThat(filter.isAllowed(ok)).as(ok).isTrue();
        }
    }

    @Test
    void nullIsNotAllowed() {
        assertThat(filter.isAllowed(null)).isFalse();
    }
}
