package com.likelion.chak.dto;

import java.time.LocalDate;
import java.util.List;

/** 수능이 끝난 뒤 보는 나의 수능 리캡. 숫자만 담고, 다른 사람의 이름이나 편지 내용은 넣지 않는다. */
public final class RecapDtos {

    private RecapDtos() {
    }

    /**
     * @param member     로그인 회원인지(비회원은 보낸 편지만 센다)
     * @param firstDay   착을 처음 쓴 날(한국 날짜). 기록이 없으면 null
     * @param received   내 책상에 온 응원. 책상이 없으면 null
     * @param sent       내가 보낸 응원
     * @param games      한 판이라도 한 미니게임만
     * @param studyType  공부 유형 결과 코드. 안 했으면 null
     */
    public record Recap(
            boolean member,
            LocalDate firstDay,
            Received received,
            Sent sent,
            List<GameStat> games,
            String studyType) {
    }

    /**
     * @param letters  받은 편지(카드) 수
     * @param stickers 받은 스티커 수
     * @param senders  보낸 사람 수(같은 사람이 여러 번 보내도 한 명)
     * @param firstDay 첫 응원이 온 날
     * @param peakDay  응원이 가장 많이 온 날
     * @param peakCount 그날 온 응원 수
     */
    public record Received(int letters, int stickers, int senders, LocalDate firstDay, LocalDate peakDay, int peakCount) {
    }

    /** @param letters 보낸 응원 수 @param desks 응원을 보낸 책상 수 */
    public record Sent(int letters, int desks) {
    }

    /**
     * @param game       GUESS_GOD · SNEAKY_PHONE · DESK_BLAST
     * @param plays      끝까지 한 판 수
     * @param best       최고 점수
     * @param topPercent 회원 최고 점수 중 내 순위(상위 몇 %, 1~100). 점수가 없으면 null
     */
    public record GameStat(String game, int plays, Integer best, Integer topPercent) {
    }
}
