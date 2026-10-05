package com.likelion.chak.dto;

import com.likelion.chak.domain.DeskObject;
import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageKind;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalMessageDelivery;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

public record OwnerMessageResponse(
        Long deliveryId,
        Long messageId,
        String authorDisplayName,
        MessageKind kind,
        MessageVisibility visibility,
        MessageStatus status,
        boolean locked,
        int schemaVersion,
        JsonNode card,
        DeskObjectResponse object,
        Instant unlockAt,
        Instant readAt,
        Instant createdAt) {

    public static OwnerMessageResponse from(
            PersonalMessageDelivery delivery,
            DeskObject deskObject,
            ObjectMapper objectMapper,
            Instant now) {
        Message message = delivery.getMessage();
        boolean locked = !delivery.isUnlocked(now);
        JsonNode card = !locked && message.getCardPayload() != null
                ? objectMapper.readTree(message.getCardPayload())
                : null;

        return new OwnerMessageResponse(
                delivery.getId(),
                message.getId(),
                message.getAuthorDisplayName(),
                message.getKind(),
                message.getVisibility(),
                message.getStatus(),
                locked,
                message.getSchemaVersion(),
                card,
                deskObject == null ? null : DeskObjectResponse.from(deskObject, objectMapper),
                delivery.getUnlockAt(),
                message.getReadAt(),
                message.getCreatedAt());
    }
}
