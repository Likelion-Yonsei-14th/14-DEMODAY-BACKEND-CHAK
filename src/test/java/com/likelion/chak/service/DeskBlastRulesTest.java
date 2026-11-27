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
        // 놓기 10번(칸 점수 최대 90) + 줄 4개 · 최고 콤보 2: 두 번에 1줄(콤보 1)·3줄(콤보 2) → 10 × (1 + 9 × 2) = 190
        // + 책상 비우기 최대 4번 1,200
        assertThat(DeskBlastRules.maxScore(10, 4, 2)).isEqualTo(90 + 190 + 1_200);
        assertThat(DeskBlastRules.maxScore(10, 0, 0)).isEqualTo(90);
    }

    @Test
    void everyPlacementEarnsAtLeastOnePoint() {
        assertThat(DeskBlastRules.minScore(25)).isEqualTo(25);
    }

    @Test
    void scoresNoRealGameCanReachAreRefused() {
        // 300번 놓아 300줄, 콤보 300: 예전 상한은 약 549만이라 500만 점 위조가 통과했다.
        assertThat(DeskBlastRules.maxScore(300, 300, 300)).isLessThan(1_000_000L);
    }

    @Test
    void clearPointsBoundMatchesABruteForceSearchOnSmallGames() {
        for (int lines = 1; lines <= 14; lines++) {
            for (int combo = 1; combo <= lines; combo++) {
                long brute = 0;
                for (int n = 1; n <= lines; n++) brute = Math.max(brute, brute(n, lines, combo, 1, 1));
                assertThat(DeskBlastRules.maxClearPoints(lines, combo, lines)).as("lines=%d combo=%d", lines, combo)
                        .isEqualTo(10 * brute);
            }
        }
    }

    /** n번 남은 순간에 lines줄을 1~6줄씩 나눠 Σ k² × min(i, combo)를 모든 경우로 찾는다(콤보 combo에 닿아야 한다). */
    private static long brute(int n, int lines, int combo, int i, int unused) {
        if (n == 0) return lines == 0 && i - 1 >= combo ? 0 : Long.MIN_VALUE / 4;
        long best = Long.MIN_VALUE / 4;
        for (int k = 1; k <= 6 && k <= lines; k++) {
            long rest = brute(n - 1, lines - k, combo, i + 1, unused);
            if (rest > Long.MIN_VALUE / 8) best = Math.max(best, (long) k * k * Math.min(i, combo) + rest);
        }
        return best;
    }
}
