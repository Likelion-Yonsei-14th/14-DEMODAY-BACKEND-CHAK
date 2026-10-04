package com.likelion.chak.service;

import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponse;
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
class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private GuestIdentityService guestIdentityService;

    @Autowired
    private DeskService deskService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void guestCanCreatePublicMessageAndReadItAfterUnlock() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);
        GuestMessageCreateRequest request = request("민준", "PUBLIC", 1);

        MessageResponse created = messageService.createGuestMessage(
                desk.getSupporterToken(),
                guestSession.getGuestIdentity(),
                request);
        List<MessageResponse> publicMessages = messageService.getPublicMessages(
                desk.getSupporterToken());

        assertThat(created.getAuthorDisplayName()).isEqualTo("민준");
        assertThat(created.getVisibility()).isEqualTo(MessageVisibility.PUBLIC);
        assertThat(publicMessages).hasSize(1);
        assertThat(publicMessages.get(0).getId()).isEqualTo(created.getId());
    }

    @Test
    void privateMessageDoesNotAppearInPublicFeed() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);

        messageService.createGuestMessage(
                desk.getSupporterToken(),
                guestSession.getGuestIdentity(),
                request("비밀친구", "PRIVATE", 1));

        assertThat(messageService.getPublicMessages(desk.getSupporterToken())).isEmpty();
    }

    @Test
    void lockedMessageDoesNotAppearInPublicFeed() {
        PersonalDesk desk = createDesk(Instant.parse("2099-01-01T00:00:00Z"));
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);

        messageService.createGuestMessage(
                desk.getSupporterToken(),
                guestSession.getGuestIdentity(),
                request("미래친구", "PUBLIC", 1));

        assertThat(messageService.getPublicMessages(desk.getSupporterToken())).isEmpty();
    }

    @Test
    void guestNicknameIsRequired() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);

        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(),
                guestSession.getGuestIdentity(),
                request(" ", "PUBLIC", 1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GUEST_NICKNAME_REQUIRED);
    }

    @Test
    void cardCannotContainMoreThanThreePages() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);
        GuestMessageCreateRequest request = objectMapper.readValue("""
                {
                  "nickname": "친구",
                  "kind": "CARD",
                  "visibility": "PUBLIC",
                  "schemaVersion": 1,
                  "card": {"pages": [{}, {}, {}, {}]},
                  "object": {
                    "representationType": "LETTER",
                    "x": 50,
                    "y": 50,
                    "rotation": 0,
                    "scale": 1,
                    "zIndex": 1
                  }
                }
                """, GuestMessageCreateRequest.class);

        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(),
                guestSession.getGuestIdentity(),
                request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CARD_PAYLOAD);
    }

    @Test
    void memberMessageUsesAccountNameWhenNicknameIsBlank() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        UserAccount author = userAccountRepository.save(
                UserAccount.createKakao("member-author-1", "계정이름", null, null));

        MessageResponse response = messageService.createUserMessage(
                desk.getSupporterToken(),
                author,
                request("", "PUBLIC", 1));

        assertThat(response.getAuthorDisplayName()).isEqualTo("계정이름");
    }

    @Test
    void memberMessagePrefersNicknameOverride() {
        PersonalDesk desk = createDesk(Instant.parse("2026-01-01T00:00:00Z"));
        UserAccount author = userAccountRepository.save(
                UserAccount.createKakao("member-author-2", "계정이름", null, null));

        MessageResponse response = messageService.createUserMessage(
                desk.getSupporterToken(),
                author,
                request("새 닉네임", "PUBLIC", 1));

        assertThat(response.getAuthorDisplayName()).isEqualTo("새 닉네임");
    }

    private PersonalDesk createDesk(Instant unlockAt) {
        UserAccount owner = userAccountRepository.save(
                UserAccount.createKakao("test-kakao-id", "지수", null, null));
        return deskService.createClaimedDesk(
                owner,
                "지수",
                ReadModeType.TIME_CAPSULE,
                null,
                unlockAt);
    }

    private GuestMessageCreateRequest request(
            String nickname,
            String visibility,
            int schemaVersion) {
        return objectMapper.readValue("""
                {
                  "nickname": "%s",
                  "kind": "CARD",
                  "visibility": "%s",
                  "schemaVersion": %d,
                  "card": {"pages": [{"id": "page-1", "elements": []}]},
                  "object": {
                    "representationType": "LETTER",
                    "assetId": "letter-basic",
                    "color": "cream",
                    "x": 50,
                    "y": 50,
                    "rotation": 0,
                    "scale": 1,
                    "zIndex": 1,
                    "metadata": {}
                  }
                }
                """.formatted(nickname, visibility, schemaVersion), GuestMessageCreateRequest.class);
    }
}
