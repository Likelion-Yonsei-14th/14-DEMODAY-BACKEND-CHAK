package com.likelion.chak.service;

import com.likelion.chak.domain.DeskBlock;
import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import com.likelion.chak.dto.CommunityDtos.BlockResponse;
import com.likelion.chak.dto.CommunityDtos.SupporterResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.DeskBlockRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 책상 주인이 작성자를 숨기거나 숨김을 해제한다. 숨긴 작성자의 편지는 주인 목록과 공개 목록에서 빠진다. */
@Service
@RequiredArgsConstructor
public class BlockService {

    private static final int MAX_SUPPORTERS_SCAN = 2000;

    private final DeskService deskService;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final DeskBlockRepository blockRepository;

    @Transactional(readOnly = true)
    public List<SupporterResponse> supporters(Long ownerId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        Map<String, DeskBlock> blocks = new HashMap<>();
        blockRepository.findAllByDesk(desk).forEach(block -> blocks.put(blockKey(block), block));

        Map<String, Aggregate> authors = new HashMap<>();
        deliveryRepository.findAllByDeskAndMessageStatusNot(
                        desk, MessageStatus.DELETED, PageRequest.of(0, MAX_SUPPORTERS_SCAN)).forEach(delivery -> {
            Message message = delivery.getMessage();
            String key = authorKey(message);
            if (key == null) {
                return;
            }
            Aggregate aggregate = authors.computeIfAbsent(key, k -> new Aggregate());
            aggregate.count++;
            if (aggregate.deliveryId == null || delivery.getId() > aggregate.deliveryId) {
                aggregate.deliveryId = delivery.getId();
                aggregate.displayName = message.getAuthorDisplayName();
            }
        });

        return authors.entrySet().stream()
                .map(entry -> {
                    DeskBlock block = blocks.get(entry.getKey());
                    Aggregate a = entry.getValue();
                    return new SupporterResponse(a.deliveryId, a.displayName, a.count,
                            block != null, block == null ? null : block.getId());
                })
                .sorted(Comparator.comparing(SupporterResponse::displayName))
                .toList();
    }

    @Transactional
    public BlockResponse block(Long ownerId, Long deliveryId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        PersonalMessageDelivery delivery = deliveryRepository.findById(deliveryId)
                .filter(d -> d.getDesk().getId().equals(desk.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        Message message = delivery.getMessage();
        DeskBlock existing = message.getAuthorUser() != null
                ? blockRepository.findByDeskAndAuthorUserId(desk, message.getAuthorUser().getId()).orElse(null)
                : message.getAuthorGuest() != null
                        ? blockRepository.findByDeskAndAuthorGuestId(desk, message.getAuthorGuest().getId()).orElse(null)
                        : null;
        DeskBlock block = existing != null ? existing : blockRepository.save(DeskBlock.create(
                desk, message.getAuthorUser(), message.getAuthorGuest(), message.getAuthorDisplayName()));
        return new BlockResponse(block.getId(), block.getDisplayName(), block.getCreatedAt());
    }

    @Transactional
    public void unblock(Long ownerId, Long blockId) {
        PersonalDesk desk = deskService.findByOwnerId(ownerId);
        DeskBlock block = blockRepository.findByIdAndDesk(blockId, desk)
                .orElseThrow(() -> new CustomException(ErrorCode.BLOCK_NOT_FOUND));
        blockRepository.delete(block);
    }

    private String authorKey(Message message) {
        if (message.getAuthorUser() != null) {
            return "u" + message.getAuthorUser().getId();
        }
        if (message.getAuthorGuest() != null) {
            return "g" + message.getAuthorGuest().getId();
        }
        return null;
    }

    private String blockKey(DeskBlock block) {
        return block.getAuthorUser() != null ? "u" + block.getAuthorUser().getId() : "g" + block.getAuthorGuest().getId();
    }

    private static final class Aggregate {
        Long deliveryId;
        String displayName;
        int count;
    }
}
