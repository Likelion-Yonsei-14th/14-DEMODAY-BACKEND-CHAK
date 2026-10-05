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
import com.likelion.chak.dto.OwnerMessageResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.DeskObjectRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
        PersonalDesk desk = deskService.findBySupporterTokenForUpdate(supporterToken);
        validateDeskOpen(desk);
        validateRequest(request, true);

        MessageVisibility visibility = resolveVisibility(desk, request.getVisibility());
        Message message = Message.createByGuest(
                guestIdentity,
                request.getNickname().trim(),
                request.getKind(),
                visibility,
                request.getSchemaVersion(),
                serialize(request.getCard()));

        return saveMessage(desk, message, request);
    }

    @Transactional
    public MessageResponse createUserMessage(
            String supporterToken,
            UserAccount user,
            GuestMessageCreateRequest request) {
        PersonalDesk desk = deskService.findBySupporterTokenForUpdate(supporterToken);
        validateDeskOpen(desk);
        validateRequest(request, false);

        MessageVisibility visibility = resolveVisibility(desk, request.getVisibility());
        Message message = Message.createByUser(
                user,
                request.getNickname(),
                request.getKind(),
                visibility,
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

        DeskObject deskObject = deskObjectRepository.save(DeskObject.create(
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

        return MessageResponse.from(delivery, deskObject, objectMapper);
    }

    @Transactional(readOnly = true)
    public SliceResponse<MessageResponse> getPublicMessages(String supporterToken, int page, int size) {
        PersonalDesk desk = deskService.findBySupporterToken(supporterToken);
        validatePage(page, size);

        if (!desk.isPublicFeedEnabled()) {
            throw new CustomException(ErrorCode.PUBLIC_FEED_DISABLED);
        }

        Slice<PersonalMessageDelivery> deliveries = deliveryRepository
                .findAllByDeskAndMessageVisibilityAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDescIdDesc(
                        desk,
                        MessageVisibility.PUBLIC,
                        MessageStatus.DELETED,
                        Instant.now(),
                        PageRequest.of(page, size));
        return mapSlice(deliveries, (delivery, object) -> MessageResponse.from(delivery, object, objectMapper));
    }

    @Transactional(readOnly = true)
    public SliceResponse<OwnerMessageResponse> getOwnerMessages(
            Long ownerId, boolean includeLocked, int page, int size) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        validatePage(page, size);
        Instant now = Instant.now();
        Slice<PersonalMessageDelivery> deliveries = includeLocked
                ? deliveryRepository.findAllByDeskAndMessageStatusNotOrderByCreatedAtDescIdDesc(
                        desk, MessageStatus.DELETED, PageRequest.of(page, size))
                : deliveryRepository.findAllByDeskAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDescIdDesc(
                        desk, MessageStatus.DELETED, now, PageRequest.of(page, size));
        return mapSlice(deliveries,
                (delivery, object) -> OwnerMessageResponse.from(delivery, object, objectMapper, now));
    }

    // 기존 내부 호출 호환용이며, 테스트/소규모 배치에서도 무제한 조회하지 않는다.
    @Transactional(readOnly = true)
    public List<MessageResponse> getPublicMessages(String supporterToken) {
        return getPublicMessages(supporterToken, 0, 100).content();
    }

    @Transactional(readOnly = true)
    public List<OwnerMessageResponse> getOwnerMessages(Long ownerId, boolean includeLocked) {
        return getOwnerMessages(ownerId, includeLocked, 0, 100).content();
    }

    @Transactional
    public OwnerMessageResponse markOwnerMessageRead(Long ownerId, Long deliveryId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        PersonalMessageDelivery delivery = findOwnerDelivery(desk, deliveryId);
        Instant now = Instant.now();
        if (!delivery.isUnlocked(now)) {
            throw new CustomException(ErrorCode.MESSAGE_LOCKED);
        }
        delivery.getMessage().markRead(now);
        return OwnerMessageResponse.from(
                delivery,
                deskObjectRepository.findByMessageId(delivery.getMessage().getId()).orElse(null),
                objectMapper,
                now);
    }

    @Transactional
    public void deleteOwnerMessage(Long ownerId, Long deliveryId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        PersonalMessageDelivery delivery = findOwnerDelivery(desk, deliveryId);
        delivery.getMessage().delete(Instant.now());
    }

    private PersonalMessageDelivery findOwnerDelivery(PersonalDesk desk, Long deliveryId) {
        return deliveryRepository.findForUpdateByIdAndDesk(deliveryId, desk)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private <T> SliceResponse<T> mapSlice(
            Slice<PersonalMessageDelivery> deliveries,
            BiFunction<PersonalMessageDelivery, DeskObject, T> mapper) {
        List<Long> messageIds = deliveries.getContent().stream()
                .map(delivery -> delivery.getMessage().getId())
                .toList();
        Map<Long, DeskObject> objectsByMessageId = messageIds.isEmpty()
                ? Map.of()
                : deskObjectRepository.findAllByMessageIdIn(messageIds).stream()
                        .collect(Collectors.toMap(object -> object.getMessage().getId(), object -> object));
        List<T> content = deliveries.getContent().stream()
                .map(delivery -> mapper.apply(
                        delivery,
                        objectsByMessageId.get(delivery.getMessage().getId())))
                .toList();
        return SliceResponse.from(deliveries, content);
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
        if (request.getSchemaVersion() == null
                || request.getSchemaVersion() != CURRENT_SCHEMA_VERSION) {
            throw new CustomException(ErrorCode.INVALID_CARD_PAYLOAD);
        }

        validateCard(request.getKind(), request.getCard());
        validateDeskObject(request.getObject());
    }

    private MessageVisibility resolveVisibility(PersonalDesk desk, MessageVisibility requested) {
        return requested == null ? desk.getDefaultMessageVisibility() : requested;
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
