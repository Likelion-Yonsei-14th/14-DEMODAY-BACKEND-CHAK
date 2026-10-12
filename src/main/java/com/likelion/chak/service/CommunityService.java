package com.likelion.chak.service;

import com.likelion.chak.domain.DeskObject;
import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageReaction;
import com.likelion.chak.domain.MessageReport;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalMessageDelivery;
import com.likelion.chak.domain.ReactionType;
import com.likelion.chak.dto.CommunityDtos.ReactionResponse;
import com.likelion.chak.dto.CommunityDtos.SentMessageResponse;
import com.likelion.chak.dto.DeskObjectResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.DeskBlockRepository;
import com.likelion.chak.repository.DeskObjectRepository;
import com.likelion.chak.repository.MessageReactionRepository;
import com.likelion.chak.repository.MessageReportRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 보낸 응원 목록, 작성자 삭제, 반응, 신고. 요청자는 회원 또는 비회원(쿠키)이다. */
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final MessageRepository messageRepository;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final DeskObjectRepository deskObjectRepository;
    private final MessageReactionRepository reactionRepository;
    private final MessageReportRepository reportRepository;
    private final DeskBlockRepository deskBlockRepository;
    private final ObjectMapper objectMapper;

    // ---------- 보낸 응원 ----------

    @Transactional(readOnly = true)
    public SliceResponse<SentMessageResponse> getSentMessages(CallerIdentity caller, int page, int size) {
        validatePage(page, size);
        if (!caller.isPresent()) {
            return new SliceResponse<>(List.of(), page, size, false);
        }
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Message> messages = caller.user() != null
                ? messageRepository.findAllByAuthorUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        caller.userId(), MessageStatus.DELETED, pageable)
                : messageRepository.findAllByAuthorGuestIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        caller.guestId(), MessageStatus.DELETED, pageable);

        List<Long> ids = messages.getContent().stream().map(Message::getId).toList();
        Map<Long, PersonalMessageDelivery> deliveries = ids.isEmpty() ? Map.of()
                : deliveryRepository.findAllByMessageIdIn(ids).stream()
                        .collect(Collectors.toMap(d -> d.getMessage().getId(), d -> d));
        Map<Long, DeskObject> objects = ids.isEmpty() ? Map.of()
                : deskObjectRepository.findAllByMessageIdIn(ids).stream()
                        .collect(Collectors.toMap(o -> o.getMessage().getId(), o -> o));

        List<SentMessageResponse> content = messages.getContent().stream()
                .map(message -> toSent(message, deliveries.get(message.getId()), objects.get(message.getId())))
                .toList();
        return SliceResponse.from(messages, content);
    }

    private SentMessageResponse toSent(Message message, PersonalMessageDelivery delivery, DeskObject object) {
        JsonNode card = message.getCardPayload() == null ? null : objectMapper.readTree(message.getCardPayload());
        return new SentMessageResponse(
                message.getId(),
                delivery == null ? null : delivery.getDesk().getDisplayName(),
                delivery == null ? null : delivery.getDesk().getSupporterToken(),
                message.getKind(),
                message.getVisibility(),
                message.getStatus(),
                isDeletable(message),
                message.getSchemaVersion(),
                card,
                object == null ? null : DeskObjectResponse.from(object, objectMapper),
                delivery == null ? null : delivery.getUnlockAt(),
                message.getReadAt(),
                message.getCreatedAt());
    }

    private boolean isDeletable(Message message) {
        return message.getVisibility() == MessageVisibility.PUBLIC && message.getStatus() == MessageStatus.SENT;
    }

    /** 작성자가 아직 읽히지 않은 자신의 공개 편지를 삭제한다. */
    @Transactional
    public void deleteOwnMessage(CallerIdentity caller, Long messageId) {
        PersonalMessageDelivery delivery = deliveryRepository.findForUpdateByMessageId(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        Message message = delivery.getMessage();
        if (message.getStatus() == MessageStatus.DELETED || !caller.isAuthorOf(message)) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (!isDeletable(message)) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_DELETABLE);
        }
        message.delete(Instant.now());
    }

    // ---------- 반응 ----------

    @Transactional
    public ReactionResponse react(CallerIdentity caller, Long messageId, ReactionType type) {
        Message message = requireVisible(caller, messageId);
        MessageReaction reaction = findReaction(caller, messageId).orElse(null);
        if (reaction == null) {
            reactionRepository.save(MessageReaction.create(message, caller.user(), caller.guest(), type));
        } else {
            reaction.change(type);
        }
        return new ReactionResponse(messageId, type);
    }

    @Transactional
    public void removeReaction(CallerIdentity caller, Long messageId) {
        findReaction(caller, messageId).ifPresent(reactionRepository::delete);
    }

    @Transactional(readOnly = true)
    public Map<Long, ReactionType> myReactions(CallerIdentity caller, Collection<Long> messageIds) {
        if (!caller.isPresent() || messageIds.isEmpty()) {
            return Map.of();
        }
        List<MessageReaction> reactions = caller.user() != null
                ? reactionRepository.findAllByUserIdAndMessageIdIn(caller.userId(), messageIds)
                : reactionRepository.findAllByGuestIdAndMessageIdIn(caller.guestId(), messageIds);
        Map<Long, ReactionType> result = new HashMap<>();
        reactions.forEach(r -> result.put(r.getMessage().getId(), r.getType()));
        return result;
    }

    private java.util.Optional<MessageReaction> findReaction(CallerIdentity caller, Long messageId) {
        return caller.user() != null
                ? reactionRepository.findByMessageIdAndUserId(messageId, caller.userId())
                : reactionRepository.findByMessageIdAndGuestId(messageId, caller.guestId());
    }

    // ---------- 신고 ----------

    @Transactional
    public void report(CallerIdentity caller, Long messageId, String reason) {
        Message message = requireVisible(caller, messageId);
        boolean exists = caller.user() != null
                ? reportRepository.existsByMessageIdAndReporterUserId(messageId, caller.userId())
                : reportRepository.existsByMessageIdAndReporterGuestId(messageId, caller.guestId());
        if (exists) {
            return;
        }
        String normalized = reason == null || reason.isBlank() ? null : reason.trim();
        reportRepository.save(MessageReport.create(message, caller.user(), caller.guest(), normalized));
    }

    // ---------- 접근 규칙 ----------

    /**
     * 요청자가 볼 수 있는 편지만 반환한다. 책상 주인은 열린 편지를, 그 밖의 사람은 공개 범위가 PUBLIC이고
     * 열렸으며 공개 목록이 켜져 있고 주인이 숨기지 않은 작성자의 편지를 볼 수 있다. 그 외에는 존재하지 않는 것처럼 404.
     */
    private Message requireVisible(CallerIdentity caller, Long messageId) {
        if (!caller.isPresent()) {
            throw new CustomException(ErrorCode.IDENTITY_REQUIRED);
        }
        PersonalMessageDelivery delivery = deliveryRepository.findByMessageId(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        Message message = delivery.getMessage();
        if (message.getStatus() == MessageStatus.DELETED || !delivery.isUnlocked(Instant.now())) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        var desk = delivery.getDesk();
        boolean owner = caller.user() != null && desk.getOwner() != null
                && desk.getOwner().getId().equals(caller.userId());
        if (owner) {
            return message;
        }
        boolean publicVisible = message.getVisibility() == MessageVisibility.PUBLIC
                && desk.isPublicFeedEnabled()
                && !isBlockedByOwner(desk, message);
        if (!publicVisible) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private boolean isBlockedByOwner(com.likelion.chak.domain.PersonalDesk desk, Message message) {
        if (message.getAuthorUser() != null
                && deskBlockRepository.findByDeskAndAuthorUserId(desk, message.getAuthorUser().getId()).isPresent()) {
            return true;
        }
        return message.getAuthorGuest() != null
                && deskBlockRepository.findByDeskAndAuthorGuestId(desk, message.getAuthorGuest().getId()).isPresent();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
