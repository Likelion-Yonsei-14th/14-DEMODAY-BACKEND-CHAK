package com.likelion.chak.dto;

import com.likelion.chak.domain.MessageKind;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.ReactionType;
import com.likelion.chak.domain.ReplyScope;
import com.likelion.chak.domain.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** 반응·답장·신고·차단·보낸 응원 관련 요청/응답 모음. */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    public record ReactionRequest(@NotNull ReactionType type) {
    }

    public record ReactionResponse(Long messageId, ReactionType myReaction) {
    }

    public record ReportRequest(@Size(max = 100) String reason) {
    }

    public record ReplyCreateRequest(
            @NotNull ReplyScope scope,
            Long sourceMessageId,
            @NotEmpty @Size(max = 100) List<@NotNull Long> targetMessageIds,
            @NotBlank @Size(max = 500) String text) {
    }

    public record ReplyResponse(
            Long id,
            ReplyScope scope,
            Long sourceMessageId,
            List<Long> targetMessageIds,
            List<String> targetSenderNames,
            String ownerName,
            String text,
            Instant createdAt) {
    }

    public record SupporterResponse(
            Long deliveryId,
            String displayName,
            int messageCount,
            boolean blocked,
            Long blockId) {
    }

    public record BlockRequest(@NotNull Long deliveryId) {
    }

    public record BlockResponse(Long id, String displayName, Instant createdAt) {
    }

    public record SentMessageResponse(
            Long messageId,
            String deskName,
            String supporterToken,
            MessageKind kind,
            MessageVisibility visibility,
            MessageStatus status,
            boolean deletable,
            int schemaVersion,
            JsonNode card,
            DeskObjectResponse object,
            Instant unlockAt,
            Instant readAt,
            Instant createdAt) {
    }

    public enum ReportAction {
        DISMISS,
        REMOVE_MESSAGE
    }

    public record ReportActionRequest(@NotNull ReportAction action) {
    }

    public record ReportResponse(
            Long id,
            Long messageId,
            String reason,
            ReportStatus status,
            String messageAuthorDisplayName,
            MessageVisibility messageVisibility,
            MessageStatus messageStatus,
            Instant createdAt,
            Instant resolvedAt) {
    }
}
