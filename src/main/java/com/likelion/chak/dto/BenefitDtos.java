package com.likelion.chak.dto;

import com.likelion.chak.domain.BenefitSubmission;
import com.likelion.chak.domain.PromotionInquiry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BenefitDtos {

    private BenefitDtos() {
    }

    public enum UploadPurpose { LOGO, SHEET }

    /** 설문 답변 전체. {@code website}는 사람 눈에 보이지 않는 칸이라 자동 입력 프로그램만 채운다(값이 있으면 저장하지 않는다). */
    public record SubmitRequest(@NotNull JsonNode answers, @Size(max = 200) String website) {
    }

    public record Received(boolean received) {
    }

    public record UploadRequest(
            @NotNull UploadPurpose purpose,
            @NotBlank @Size(max = 200) String fileName,
            @Positive long size) {
    }

    public record UploadResponse(String key, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {
    }

    public record FileLink(String field, String fileName, String url) {
    }

    public record SubmissionSummary(
            Long id,
            String brand,
            String category,
            String contactName,
            String email,
            String phone,
            String headline,
            PromotionInquiry.Status status,
            Instant createdAt,
            Instant handledAt) {

        public static SubmissionSummary from(BenefitSubmission s) {
            return new SubmissionSummary(s.getId(), s.getBrand(), s.getCategory(), s.getContactName(), s.getEmail(),
                    s.getPhone(), s.getHeadline(), s.getStatus(), s.getCreatedAt(), s.getHandledAt());
        }
    }

    public record SubmissionDetail(SubmissionSummary summary, JsonNode answers, List<FileLink> files) {
    }

    public record StatusRequest(@NotNull PromotionInquiry.Status status) {
    }
}
