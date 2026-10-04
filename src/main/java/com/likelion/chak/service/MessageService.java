package com.likelion.chak.service;

import com.likelion.chak.domain.ClaimStatus;
import com.likelion.chak.domain.DeskObject;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageKind;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskObjectRequest;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.MessageResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.DeskObjectRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_CARD_PAYLOAD_LENGTH = 500_000;

    private final DeskService deskService;
    private final UnlockTimeCalculator unlockTimeCalculator;
    private final MessageRepository messageRepository;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final DeskObjectRepository deskObjectRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MessageResponse createGuestMessage(
            String supporterToken,
            GuestIdentity guestIdentity,
            GuestMessageCreateRequest request) {
        PersonalDesk desk = deskService.findBySupporterToken(supporterToken);
        validateDeskOpen(desk);
        validateRequest(request, true);

        Message message = Message.createByGuest(
                guestIdentity,
                request.getNickname().trim(),
                request.getKind(),
                request.getVisibility(),
                request.getSchemaVersion(),
                serialize(request.getCard()));

        return saveMessage(desk, message, request);
    }

    @Transactional
    public MessageResponse createUserMessage(
            String supporterToken,
            UserAccount user,
            GuestMessageCreateRequest request) {
        PersonalDesk desk = deskService.findBySupporterToken(supporterToken);
        validateDeskOpen(desk);
        validateRequest(request, false);

        Message message = Message.createByUser(
                user,
                request.getNickname(),
                request.getKind(),
                request.getVisibility(),
                request.getSchemaVersion(),
                serialize(request.getCard()));

        return saveMessage(desk, message, request);
    }

    private MessageResponse saveMessage(
            PersonalDesk desk,
            Message message,
            GuestMessageCreateRequest request) {

        DeskObjectRequest objectRequest = request.getObject();
        String metadataPayload = serialize(objectRequest.getMetadata());
        Instant createdAt = Instant.now();

        message = messageRepository.save(message);

        Instant unlockAt = request.getKind() == MessageKind.STICKER
                ? createdAt
                : unlockTimeCalculator.calculate(desk, createdAt);

        PersonalMessageDelivery delivery = deliveryRepository.save(
                PersonalMessageDelivery.create(
                        message,
                        desk,
                        unlockAt,
                        desk.getClaimStatus() == ClaimStatus.UNCLAIMED));

        deskObjectRepository.save(DeskObject.create(
                desk,
                message,
                objectRequest.getRepresentationType(),
                objectRequest.getAssetId(),
                objectRequest.getColor(),
                objectRequest.getX(),
                objectRequest.getY(),
                objectRequest.getRotation(),
                objectRequest.getScale(),
                objectRequest.getZIndex(),
                metadataPayload));

        return MessageResponse.from(delivery, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getPublicMessages(String supporterToken) {
        PersonalDesk desk = deskService.findBySupporterToken(supporterToken);

        if (!desk.isPublicFeedEnabled()) {
            throw new CustomException(ErrorCode.PUBLIC_FEED_DISABLED);
        }

        return deliveryRepository
                .findAllByDeskAndMessageVisibilityAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDesc(
                        desk,
                        MessageVisibility.PUBLIC,
                        MessageStatus.DELETED,
                        Instant.now())
                .stream()
                .map(delivery -> MessageResponse.from(delivery, objectMapper))
                .toList();
    }

    private void validateDeskOpen(PersonalDesk desk) {
        if (desk.isRoomClosed()) {
            throw new CustomException(ErrorCode.DESK_CLOSED);
        }
    }

    private void validateRequest(GuestMessageCreateRequest request, boolean guest) {
        if (guest && (request.getNickname() == null || request.getNickname().isBlank())) {
            throw new CustomException(ErrorCode.GUEST_NICKNAME_REQUIRED);
        }
        if (request.getNickname() != null && request.getNickname().trim().length() > 50) {
            throw new CustomException(ErrorCode.GUEST_NICKNAME_REQUIRED);
        }
        if (request.getKind() == null) {
            throw new CustomException(ErrorCode.INVALID_MESSAGE_KIND);
        }
        if (request.getVisibility() == null) {
            throw new CustomException(ErrorCode.INVALID_MESSAGE_VISIBILITY);
        }
        if (request.getSchemaVersion() == null
                || request.getSchemaVersion() != CURRENT_SCHEMA_VERSION) {
            throw new CustomException(ErrorCode.INVALID_CARD_PAYLOAD);
        }

        validateCard(request.getKind(), request.getCard());
        validateDeskObject(request.getObject());
    }

    private void validateCard(MessageKind kind, JsonNode card) {
        if (kind == MessageKind.STICKER) {
            return;
        }
        if (card == null || !card.isObject()) {
            throw new CustomException(ErrorCode.INVALID_CARD_PAYLOAD);
        }
        JsonNode pages = card.get("pages");
        if (pages == null || !pages.isArray() || pages.isEmpty() || pages.size() > 3) {
            throw new CustomException(ErrorCode.INVALID_CARD_PAYLOAD);
        }
        if (card.toString().length() > MAX_CARD_PAYLOAD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_CARD_PAYLOAD);
        }
    }

    private void validateDeskObject(DeskObjectRequest object) {
        if (object == null
                || object.getRepresentationType() == null
                || object.getX() == null
                || object.getY() == null
                || object.getRotation() == null
                || object.getScale() == null
                || object.getZIndex() == null) {
            throw new CustomException(ErrorCode.INVALID_DESK_OBJECT);
        }
        if (object.getX() < 0 || object.getX() > 100
                || object.getY() < 0 || object.getY() > 100
                || object.getScale() <= 0 || object.getScale() > 3) {
            throw new CustomException(ErrorCode.INVALID_DESK_OBJECT);
        }
    }

    private String serialize(JsonNode value) {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }
}
