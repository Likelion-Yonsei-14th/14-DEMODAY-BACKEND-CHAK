package com.likelion.chak.service;

import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.DeskSettingsRequest;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.OwnerMessageResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OwnerDeskServiceTest {

    @Autowired
    private DeskService deskService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private GuestIdentityService guestIdentityService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void oneUserCanCreateOnlyOneDesk() {
        UserAccount owner = saveUser("owner-one-desk");
        DeskCreateRequest request = createRequest(Instant.parse("2026-01-01T00:00:00Z"));

        deskService.createMyDesk(owner, request);

        assertThatThrownBy(() -> deskService.createMyDesk(owner, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DESK_ALREADY_EXISTS);
    }

    @Test
    void visibilitySettingCanBeAppliedToExistingMessages() {
        UserAccount owner = saveUser("owner-bulk-visibility");
        deskService.createMyDesk(owner, createRequest(Instant.parse("2026-01-01T00:00:00Z")));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        createGuestMessage(desk, "PUBLIC");

        deskService.updateMyDesk(owner.getId(), new DeskSettingsRequest(
                "새 책상 이름",
                ReadModeType.TIME_CAPSULE,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                true,
                MessageVisibility.PRIVATE,
                true));

        List<OwnerMessageResponse> messages = messageService.getOwnerMessages(owner.getId(), true);
        assertThat(messages).singleElement()
                .extracting(OwnerMessageResponse::visibility)
                .isEqualTo(MessageVisibility.PRIVATE);
        assertThat(messageService.getPublicMessages(desk.getSupporterToken())).isEmpty();
    }

    @Test
    void lockedMessageCannotBeMarkedRead() {
        UserAccount owner = saveUser("owner-locked-message");
        deskService.createMyDesk(owner, createRequest(Instant.parse("2099-01-01T00:00:00Z")));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        createGuestMessage(desk, "PRIVATE");
        OwnerMessageResponse message = messageService.getOwnerMessages(owner.getId(), true).get(0);

        assertThat(message.locked()).isTrue();
        assertThat(message.card()).isNull();
        assertThatThrownBy(() -> messageService.markOwnerMessageRead(owner.getId(), message.deliveryId()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_LOCKED);
    }

    @Test
    void ownerCanReadAndDeleteUnlockedMessage() {
        UserAccount owner = saveUser("owner-read-delete");
        deskService.createMyDesk(owner, createRequest(Instant.parse("2026-01-01T00:00:00Z")));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        createGuestMessage(desk, "PRIVATE");
        OwnerMessageResponse message = messageService.getOwnerMessages(owner.getId(), true).get(0);

        OwnerMessageResponse read = messageService.markOwnerMessageRead(owner.getId(), message.deliveryId());
        assertThat(read.status()).isEqualTo(MessageStatus.READ);
        assertThat(read.card()).isNotNull();

        messageService.deleteOwnerMessage(owner.getId(), message.deliveryId());
        assertThat(messageService.getOwnerMessages(owner.getId(), true)).isEmpty();
    }

    @Test
    void onlyReadMessagesCanBeBasketedAndListFiltersByBasket() {
        UserAccount owner = saveUser("owner-basket");
        deskService.createMyDesk(owner, createRequest(Instant.parse("2026-01-01T00:00:00Z")));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        createGuestMessage(desk, "PRIVATE");
        createGuestMessage(desk, "PRIVATE");
        List<OwnerMessageResponse> messages = messageService.getOwnerMessages(owner.getId(), true);
        Long readId = messages.get(0).deliveryId();
        Long unreadId = messages.get(1).deliveryId();
        messageService.markOwnerMessageRead(owner.getId(), readId);

        assertThatThrownBy(() -> messageService.basketOwnerMessages(owner.getId(), List.of(readId, unreadId)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_READ);

        List<OwnerMessageResponse> basketed = messageService.basketOwnerMessages(owner.getId(), List.of(readId, readId));
        assertThat(basketed).singleElement().satisfies(item -> assertThat(item.basketedAt()).isNotNull());

        assertThat(messageService.getOwnerMessages(owner.getId(), true, true, 0, 20).content())
                .extracting(OwnerMessageResponse::deliveryId).containsExactly(readId);
        assertThat(messageService.getOwnerMessages(owner.getId(), true, false, 0, 20).content())
                .extracting(OwnerMessageResponse::deliveryId).containsExactly(unreadId);
        assertThat(messageService.getOwnerMessages(owner.getId(), true, null, 0, 20).content()).hasSize(2);
    }

    @Test
    void ownerMessagesArePaged() {
        UserAccount owner = saveUser("owner-paged-messages");
        deskService.createMyDesk(owner, createRequest(Instant.parse("2026-01-01T00:00:00Z")));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        createGuestMessage(desk, "PRIVATE");
        createGuestMessage(desk, "PRIVATE");

        SliceResponse<OwnerMessageResponse> firstPage = messageService.getOwnerMessages(
                owner.getId(), true, 0, 1);

        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(1);
    }

    private UserAccount saveUser(String kakaoId) {
        return userAccountRepository.save(UserAccount.createKakao(kakaoId, "책상주인", null, null));
    }

    private DeskCreateRequest createRequest(Instant unlockAt) {
        return new DeskCreateRequest(
                "응원 책상",
                ReadModeType.TIME_CAPSULE,
                null,
                unlockAt,
                true,
                MessageVisibility.PUBLIC);
    }

    private void createGuestMessage(PersonalDesk desk, String visibility) {
        GuestSession guest = guestIdentityService.resolveOrCreate(null);
        GuestMessageCreateRequest request = objectMapper.readValue("""
                {
                  "nickname": "응원친구",
                  "kind": "CARD",
                  "visibility": "%s",
                  "schemaVersion": 1,
                  "card": {"pages": [{"id": "page-1", "elements": []}]},
                  "object": {
                    "representationType": "LETTER",
                    "x": 50,
                    "y": 50,
                    "rotation": 0,
                    "scale": 1,
                    "zIndex": 1
                  }
                }
                """.formatted(visibility), GuestMessageCreateRequest.class);
        messageService.createGuestMessage(desk.getSupporterToken(), guest.getGuestIdentity(), request);
    }
}
