package com.likelion.chak.dto;

import com.likelion.chak.domain.StoreOutreach;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** 어드민 > 매장 연락 현황. */
public final class OutreachDtos {

    private OutreachDtos() {
    }

    public record OutreachRequest(
            StoreOutreach.Kind kind,
            @NotBlank @Size(max = 100) String storeName,
            @Size(max = 50) String category,
            @Size(max = 50) String region,
            @NotNull StoreOutreach.Channel channel,
            @Size(max = 300) String contact,
            @Size(max = 30) String assignee,
            StoreOutreach.Status status,
            @Size(max = 2000) String note,
            LocalDate lastContactedOn,
            @PositiveOrZero Integer offerPrice,
            @PositiveOrZero Integer expectedProfit,
            @PositiveOrZero Integer unitPrice,
            @Size(max = 500) String proposalUrl) {
    }

    public record OutreachResponse(
            Long id,
            StoreOutreach.Kind kind,
            String storeName,
            String category,
            String region,
            StoreOutreach.Channel channel,
            String contact,
            String assignee,
            StoreOutreach.Status status,
            String note,
            LocalDate lastContactedOn,
            Integer offerPrice,
            Integer expectedProfit,
            Integer unitPrice,
            String proposalUrl,
            String updatedByName,
            Instant createdAt,
            Instant updatedAt) {

        public static OutreachResponse from(StoreOutreach o) {
            return new OutreachResponse(o.getId(), o.getKind(), o.getStoreName(), o.getCategory(), o.getRegion(), o.getChannel(),
                    o.getContact(), o.getAssignee(), o.getStatus(), o.getNote(), o.getLastContactedOn(),
                    o.getOfferPrice(), o.getExpectedProfit(), o.getUnitPrice(), o.getProposalUrl(),
                    o.getUpdatedByName(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
