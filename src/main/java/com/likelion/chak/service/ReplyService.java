package com.likelion.chak.service;

import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageReply;
import com.likelion.chak.domain.MessageReplyTarget;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.CommunityDtos.ReplyCreateRequest;
import com.likelion.chak.dto.CommunityDtos.ReplyResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MessageReplyRepository;
import com.likelion.chak.repository.MessageReplyTargetRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 책상 주인의 일방향 답장. 한 편지에는 답장을 한 번만 보낼 수 있다. */
@Service
@RequiredArgsConstructor
public class ReplyService {

    private final DeskService deskService;
    private final AuthService authService;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final MessageReplyRepository replyRepository;
    private final MessageReplyTargetRepository targetRepository;

    @Transactional
    public ReplyResponse create(Long ownerId, ReplyCreateRequest request) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        UserAccount owner = authService.getUser(ownerId);
        List<Long> ids = request.targetMessageIds().stream().distinct().toList();
        String text = request.text().trim();
        if (text.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REPLY);
        }

        List<PersonalMessageDelivery> deliveries = deliveryRepository.findAllByMessageIdIn(ids);
        Instant now = Instant.now();
        boolean valid = deliveries.size() == ids.size() && deliveries.stream().allMatch(d ->
                d.getDesk().getId().equals(desk.getId())
                        && d.getMessage().getStatus() != MessageStatus.DELETED
                        && d.isUnlocked(now));
        if (!valid) {
            throw new CustomException(ErrorCode.INVALID_REPLY);
        }
        if (!targetRepository.findAllByMessageIdIn(ids).isEmpty()) {
            throw new CustomException(ErrorCode.REPLY_ALREADY_EXISTS);
        }

        Message source = null;
        if (request.sourceMessageId() != null) {
            source = deliveries.stream().map(PersonalMessageDelivery::getMessage)
                    .filter(m -> m.getId().equals(request.sourceMessageId()))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REPLY));
        }

        MessageReply reply = replyRepository.save(MessageReply.create(desk, owner, request.scope(), source, text));
        List<MessageReplyTarget> targets = deliveries.stream()
                .map(d -> targetRepository.save(MessageReplyTarget.create(reply, d.getMessage())))
                .toList();
        return toResponse(reply, targets.stream().map(MessageReplyTarget::getMessage).toList());
    }

    @Transactional(readOnly = true)
    public List<ReplyResponse> listForOwner(Long ownerId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        List<MessageReply> replies = replyRepository.findAllByDeskOrderByCreatedAtDescIdDesc(desk);
        if (replies.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Message>> byReply = targetRepository.findAllByReplyIn(replies).stream()
                .collect(Collectors.groupingBy(t -> t.getReply().getId(),
                        Collectors.mapping(MessageReplyTarget::getMessage, Collectors.toList())));
        return replies.stream()
                .map(reply -> toResponse(reply, byReply.getOrDefault(reply.getId(), List.of())))
                .toList();
    }

    /** 내가 보낸 편지에 달린 답장. 대상 목록에는 내 편지만 포함한다. */
    @Transactional(readOnly = true)
    public List<ReplyResponse> listForAuthor(CallerIdentity caller) {
        if (!caller.isPresent()) {
            return List.of();
        }
        Map<Long, List<MessageReplyTarget>> byReply = new LinkedHashMap<>();
        targetRepository.findAllForAuthor(caller.userId(), caller.guestId())
                .forEach(t -> byReply.computeIfAbsent(t.getReply().getId(), k -> new java.util.ArrayList<>()).add(t));
        return byReply.values().stream()
                .map(targets -> toResponse(targets.get(0).getReply(),
                        targets.stream().map(MessageReplyTarget::getMessage).toList()))
                .toList();
    }

    private ReplyResponse toResponse(MessageReply reply, List<Message> targets) {
        return new ReplyResponse(
                reply.getId(),
                reply.getScope(),
                reply.getSourceMessage() == null ? null : reply.getSourceMessage().getId(),
                targets.stream().map(Message::getId).toList(),
                new LinkedHashSet<>(targets.stream().map(Message::getAuthorDisplayName).toList()).stream().toList(),
                reply.getDesk().getDisplayName(),
                reply.getText(),
                reply.getCreatedAt());
    }
}
