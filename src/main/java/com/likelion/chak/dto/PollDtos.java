package com.likelion.chak.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PollDtos {

    private PollDtos() {
    }

    public record OptionView(long id, String label) {
    }

    /** 지역 하나의 결과. counts는 options와 같은 순서. */
    public record RegionResult(String region, long total, List<Long> counts) {
    }

    /**
     * 결과. 투표했거나 닫힌 질문에만 붙는다. counts는 options와 같은 순서.
     * regions는 표가 일정 수 이상 모인 지역만, 많은 순서로 담는다.
     */
    public record Results(long total, List<Long> counts, List<RegionResult> regions) {
    }

    /** status: upcoming(곧 열림), open(투표 중), closed(마감). */
    public record PollView(
            long id,
            String slug,
            String tag,
            String question,
            Instant opensAt,
            Instant closesAt,
            String status,
            List<OptionView> options,
            Long myOptionId,
            String myRegion,
            Results results) {
    }

    public record PollList(Instant now, List<PollView> polls) {
    }

    public record VoteRequest(
            @NotNull Long optionId,
            @Size(max = 8) String geoCountry,
            @Size(max = 40) String geoRegion) {
    }

    public record AdminPollRequest(
            @NotNull @Size(min = 1, max = 60) String slug,
            @NotNull @Size(min = 1, max = 30) String tag,
            @NotNull @Size(min = 1, max = 120) String question,
            @NotNull @Size(min = 2, max = 6) List<@NotNull @Size(min = 1, max = 40) String> options,
            @NotNull Instant opensAt,
            Instant closesAt,
            Integer sortOrder) {
    }

    public record AdminPollUpdate(Boolean hidden, Instant opensAt, Instant closesAt, Boolean clearClosesAt) {
    }

    public record AdminPollSummary(
            long id,
            String slug,
            String tag,
            String question,
            Instant opensAt,
            Instant closesAt,
            int sortOrder,
            boolean hidden,
            String status,
            List<OptionView> options,
            Results results) {
    }
}
