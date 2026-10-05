package com.likelion.chak.dto;

import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageKind;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalMessageDelivery;
import com.likelion.chak.domain.DeskObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private String authorDisplayName;
    private MessageKind kind;
    private MessageVisibility visibility;
    private int schemaVersion;
    private JsonNode card;
    private DeskObjectResponse object;
    private Instant unlockAt;
    private Instant createdAt;

    public static MessageResponse from(
            PersonalMessageDelivery delivery,
            DeskObject deskObject,
            ObjectMapper objectMapper) {
        Message message = delivery.getMessage();
        JsonNode card = null;

        if (message.getCardPayload() != null) {
            card = objectMapper.readTree(message.getCardPayload());
        }

        return new MessageResponse(
                message.getId(),
                message.getAuthorDisplayName(),
                message.getKind(),
                message.getVisibility(),
                message.getSchemaVersion(),
                card,
                deskObject == null ? null : DeskObjectResponse.from(deskObject, objectMapper),
                delivery.getUnlockAt(),
                message.getCreatedAt());
    }
}
