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
     * 줄 점수는 줄 수² × 콤보이고, 한 번에 6줄 이하라서 줄 수² ≤ 6 × 줄 수이므로 전체는 10 × 6 × 최고 콤보 × 줄 수 이하다.
     */
    static long maxScore(int placements, int lines, int bestCombo) {
        long cells = (long) MAX_CELLS_PER_PLACEMENT * placements;
        long clears = (long) LINE_BASE * MAX_LINES_PER_PLACEMENT * bestCombo * lines;
        long allClears = (long) ALL_CLEAR_BONUS * lines;
        return cells + clears + allClears;
    }

    /** 놓을 때마다 적어도 한 칸 점수는 받는다. */
    static long minScore(int placements) {
        return placements;
    }
}
