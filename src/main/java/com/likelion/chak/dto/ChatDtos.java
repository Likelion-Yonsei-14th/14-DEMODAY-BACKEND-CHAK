package com.likelion.chak.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatTurn(@NotBlank @Pattern(regexp = "user|assistant") String role,
                           @NotBlank @Size(max = 2000) String content) {
    }

    /** 마지막 말은 사용자의 질문(500자 이내). 앞의 대화는 최근 몇 개만 쓴다. */
    public record ChatRequest(@NotEmpty @Size(max = 20) List<@Valid ChatTurn> messages) {
    }

    public record DiscountBrief(Long id, String brand, String category, String benefit, String area,
                                LocalDate endDate, boolean sponsored, String imageUrl) {
    }

    public record Source(String title, String url, String hostname) {
    }

    public record ChatResponse(String answer, boolean offTopic, List<DiscountBrief> discounts, List<Source> sources) {
    }
}
