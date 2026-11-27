package com.likelion.chak.service;

/**
 * 수능 블라스트의 점수 규칙(프런트 {@code deskBlastEngine.ts}와 같은 값).
 * 서버는 게임을 다시 돌려 보지 않고, 클라이언트가 보낸 숫자들이 규칙상 함께 나올 수 있는지만 본다.
 */
final class DeskBlastRules {

    /** 한 번 놓을 때 줄 수 있는 가장 큰 블록은 3x3 한 덩어리(9칸). */
    static final int MAX_CELLS_PER_PLACEMENT = 9;
    /** 한 번 놓아 완성할 수 있는 줄 수의 상한: 3x3은 가로 3줄과 세로 3줄, 1x5는 가로 1줄과 세로 5줄. */
    static final int MAX_LINES_PER_PLACEMENT = 6;
    /** 줄 하나는 8칸으로 이루어지고 한 칸은 가로와 세로 두 줄에 속할 수 있어, 블록 한 칸당 줄은 많아야 2/8개. */
    static final int LINE_CELLS = 8;
    static final int LINE_BASE = 10;
    static final int ALL_CLEAR_BONUS = 300;
    /** 사람이 블록 하나를 집어 놓는 데 걸리는 최소 시간. 이보다 빠르면 기계로 본다. */
    static final long MIN_MS_PER_PLACEMENT = 200;
    /** 시간 오차(서버·브라우저 시계, 네트워크)를 봐주는 여유. */
    static final long TIME_SLACK_MS = 2_000;

    private DeskBlastRules() {
    }

    /** 놓은 횟수로 낼 수 있는 가장 많은 줄 수. */
    static long maxLines(int placements) {
        long byPlacement = (long) MAX_LINES_PER_PLACEMENT * placements;
        long byCells = (long) MAX_CELLS_PER_PLACEMENT * placements * 2 / LINE_CELLS;
        return Math.min(byPlacement, byCells);
    }

    /**
     * 놓은 횟수·줄 수·최고 콤보가 주어졌을 때 낼 수 있는 가장 높은 점수.
     * 칸 점수(놓을 때마다 최대 9) + 줄 점수 + 책상 비우기 보너스의 상한을 더한다.
     *
     * <p>줄 점수는 줄을 지운 i번째 순간마다 {@code 10 × k_i² × c_i}다(k_i ≤ 6줄, 콤보 c_i는 지울 때마다 1씩 올라
     * c_i ≤ min(i, 최고 콤보)). 지운 순간의 수 n은 줄 수와 놓은 횟수를 넘지 않는다. 볼록 함수라서 같은 n이면 큰 k를
     * 높은 콤보에 몰아줄 때 가장 크므로, n마다 그 값을 구해 가장 큰 것을 쓴다. 예전 상한(10 × 6 × 콤보 × 줄 수)은
     * 실제로 낼 수 없는 점수까지 받아 랭킹 조작에 쓰였다.
     */
    static long maxScore(int placements, int lines, int bestCombo) {
        long cells = (long) MAX_CELLS_PER_PLACEMENT * placements;
        long maxEvents = Math.min(lines, placements);
        long allClears = (long) ALL_CLEAR_BONUS * maxEvents;
        return cells + maxClearPoints(lines, bestCombo, maxEvents) + allClears;
    }

    /** 위 설명의 줄 점수 상한. */
    static long maxClearPoints(int lines, int bestCombo, long maxEvents) {
        if (lines <= 0 || bestCombo <= 0 || maxEvents <= 0) {
            return 0;
        }
        long best = 0;
        long minEvents = (lines + MAX_LINES_PER_PLACEMENT - 1) / MAX_LINES_PER_PLACEMENT;
        // 최고 콤보 B에 닿으려면 적어도 B번은 지워야 한다.
        for (long n = Math.max(minEvents, bestCombo); n <= maxEvents; n++) {
            best = Math.max(best, clearPointsFor(n, lines, bestCombo));
        }
        return (long) LINE_BASE * best;
    }

    /** n번 지워 줄 lines개를 없앨 때 Σ k² × min(i, B)의 최댓값(1 ≤ k ≤ 6, 큰 k를 뒤쪽 높은 콤보에). */
    private static long clearPointsFor(long n, long lines, long bestCombo) {
        long extra = lines - n;                         // 모든 순간에 1줄씩 준 뒤 남는 줄
        long sixes = Math.min(n, extra / (MAX_LINES_PER_PLACEMENT - 1));
        long rest = extra - sixes * (MAX_LINES_PER_PLACEMENT - 1);
        long middle = rest > 0 && sixes < n ? 1 : 0;   // 남은 줄을 한 순간에 몰아준다
        long ones = n - sixes - middle;
        // 순간 1..ones는 k=1, 다음 하나는 k=1+rest, 마지막 sixes개는 k=6.
        long total = comboSum(1, ones, bestCombo);
        if (middle == 1) {
            long k = 1 + rest;
            total += k * k * Math.min(ones + 1, bestCombo);
        }
        total += (long) MAX_LINES_PER_PLACEMENT * MAX_LINES_PER_PLACEMENT * comboSum(n - sixes + 1, n, bestCombo);
        return total;
    }

    /** Σ_{i=from..to} min(i, B). */
    private static long comboSum(long from, long to, long bestCombo) {
        if (to < from) {
            return 0;
        }
        long total = 0;
        long rampEnd = Math.min(to, bestCombo);
        if (from <= rampEnd) {
            total += (from + rampEnd) * (rampEnd - from + 1) / 2;
        }
        long flatFrom = Math.max(from, bestCombo + 1);
        if (flatFrom <= to) {
            total += bestCombo * (to - flatFrom + 1);
        }
        return total;
    }

    /** 놓을 때마다 적어도 한 칸 점수는 받는다. */
    static long minScore(int placements) {
        return placements;
    }
}
