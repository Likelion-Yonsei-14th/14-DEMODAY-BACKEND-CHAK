package com.likelion.chak.dto;

import com.likelion.chak.domain.PromotionInquiry;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    /**
     * 광고 상담 신청. 회사·담당자·이메일만 필수이고 나머지는 비워도 된다. {@code website}는 사람 눈에 보이지 않는 칸이라 자동 입력 프로그램만 채운다(값이 있으면 저장하지 않는다).
     */
    public record InquiryRequest(
            @NotBlank @Size(max = 100) String company,
            @NotBlank @Size(max = 50) String contactName,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 30) @Pattern(regexp = "^[0-9+\\-() ]*$") String phone,
            @Size(max = 50) String category,
            PromotionInquiry.Interest interest,
            @Size(max = 2000) String message,
            @AssertTrue boolean consent,
            @Size(max = 200) String website) {
    }

    public record InquiryReceived(boolean received) {
    }

    public record InquiryResponse(
            Long id,
            String company,
            String contactName,
            String email,
            String phone,
            String category,
            PromotionInquiry.Interest interest,
            String message,
            PromotionInquiry.Status status,
            Instant createdAt,
            Instant handledAt) {

        public static InquiryResponse from(PromotionInquiry inquiry) {
            return new InquiryResponse(inquiry.getId(), inquiry.getCompany(), inquiry.getContactName(),
                    inquiry.getEmail(), inquiry.getPhone(), inquiry.getCategory(), inquiry.getInterest(),
                    inquiry.getMessage(), inquiry.getStatus(), inquiry.getCreatedAt(), inquiry.getHandledAt());
        }
    }

    public record InquiryStatusRequest(@NotNull PromotionInquiry.Status status) {
    }
}
