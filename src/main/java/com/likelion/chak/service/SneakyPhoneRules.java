package com.likelion.chak.service;

/**
 * 선생님 몰래 폰 하기의 점수 규칙(프런트 {@code sneakyPhoneEngine.ts}와 같은 값).
 * 서버는 게임을 다시 돌려 보지 않고, 점수가 규칙상 나올 수 있는 값인지만 본다.
 */
final class SneakyPhoneRules {

    static final double GAME_MS = 30_000;
    static final int SURVIVAL_BONUS = 500;
    static final double BASE_POINTS_PER_SECOND = 100;
    /** 폰을 끊김 없이 든 시간별 배율: 0초부터 1배, 1초 1.5배, 2초 2배, 3초 이상 3배. */
    private static final double[][] TIERS = {{0, 1}, {1_000, 1.5}, {2_000, 2}, {3_000, 3}};

    /** 시간 오차(서버·브라우저 시계, 네트워크)를 봐주는 여유. */
    static final double TIME_SLACK_MS = 1_500;

    private SneakyPhoneRules() {
    }

    /** 폰을 든 시간이 {@code phoneMs}일 때 얻을 수 있는 최고 기본 점수: 한 번도 끊지 않고 들고 있었을 때. */
    static int maxBaseScore(double phoneMs) {
        double points = 0;
        double cursor = 0;
        while (cursor < phoneMs) {
            double multiplier = TIERS[0][1];
            double nextFrom = Double.POSITIVE_INFINITY;
            for (double[] tier : TIERS) {
                if (cursor >= tier[0]) {
                    multiplier = tier[1];
                } else {
                    nextFrom = Math.min(nextFrom, tier[0]);
                }
            }
            double end = Math.min(phoneMs, nextFrom);
            points += BASE_POINTS_PER_SECOND * multiplier * (end - cursor) / 1000;
            cursor = end;
        }
        return (int) Math.floor(points + 1e-6) + 1;
    }
}
