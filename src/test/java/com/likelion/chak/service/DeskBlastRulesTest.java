package com.likelion.chak.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeskBlastRulesTest {

    @Test
    void linesAreCappedByWhatOnePlacementCanClear() {
        assertThat(DeskBlastRules.maxLines(1)).isEqualTo(2);       // 한 칸 점수 9칸 × 2 ÷ 8 = 2.25, 내림
        assertThat(DeskBlastRules.maxLines(4)).isEqualTo(9);
        assertThat(DeskBlastRules.maxLines(100)).isEqualTo(225);
    }

    @Test
    void morePlacementsNeverLowerTheLimits() {
        long previousLines = 0;
        long previousScore = 0;
        for (int placements = 1; placements <= 500; placements += 7) {
            long lines = DeskBlastRules.maxLines(placements);
            long score = DeskBlastRules.maxScore(placements, (int) lines, Math.max(1, (int) Math.min(lines, placements)));
            assertThat(lines).isGreaterThanOrEqualTo(previousLines);
            assertThat(score).isGreaterThanOrEqualTo(previousScore);
            previousLines = lines;
            previousScore = score;
        }
    }

    @Test
    void maxScoreAddsPlacedCellsClearsAndEmptyDeskBonuses() {
        // 놓기 10번(칸 점수 최대 90) + 줄 4개 · 최고 콤보 2 → 10 × 6 × 2 × 4 = 480 + 책상 비우기 최대 4번 1,200
        assertThat(DeskBlastRules.maxScore(10, 4, 2)).isEqualTo(90 + 480 + 1_200);
        assertThat(DeskBlastRules.maxScore(10, 0, 0)).isEqualTo(90);
    }

    @Test
    void everyPlacementEarnsAtLeastOnePoint() {
        assertThat(DeskBlastRules.minScore(25)).isEqualTo(25);
    }
}
