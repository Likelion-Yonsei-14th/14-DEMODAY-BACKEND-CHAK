package com.likelion.chak.service;

import com.likelion.chak.domain.ClaimStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.FriendDeskDtos.FriendDeskResponse;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제로 커밋해서(롤백 없이) 넘겨받기·삭제·동시 요청을 확인한다. */
@SpringBootTest
class FriendDeskServiceTest {

    @Autowired private FriendDeskService friendDeskService;
    @Autowired private DeskService deskService;
    @Autowired private MessageService messageService;
    @Autowired private GuestIdentityService guestIdentityService;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private PersonalDeskRepository deskRepository;
    @Autowired private ObjectMapper objectMapper;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private DeskCreateRequest deskRequest(String name) {
        return new DeskCreateRequest(name, ReadModeType.TIME_CAPSULE, null, Instant.parse("2099-01-01T00:00:00Z"), null, null);
    }

    private void leaveCheer(String supporterToken, String nickname) {
        GuestSession guest = guestIdentityService.resolveOrCreate(null);
        messageService.createGuestMessage(supporterToken, guest.getGuestIdentity(), objectMapper.readValue("""
                {"nickname":"%s","kind":"CARD","visibility":"PRIVATE","schemaVersion":1,
                 "card":{"pages":[{"id":"p","elements":[]}]},
                 "object":{"representationType":"LETTER","assetId":"letter-basic","color":"cream",
                           "x":50,"y":50,"rotation":0,"scale":1,"zIndex":1,"metadata":{}}}
                """.formatted(nickname), GuestMessageCreateRequest.class));
    }

    @Test
    void creatorGetsTwoLinksAndSeesOnlyCounts() {
        UserAccount creator = user("만든이");
        FriendDeskResponse desk = friendDeskService.create(creator, deskRequest("지수"));
        assertThat(desk.supporterToken()).isNotBlank();
        assertThat(desk.claimToken()).isNotBlank().isNotEqualTo(desk.supporterToken());

        leaveCheer(desk.supporterToken(), "민준");
        leaveCheer(desk.supporterToken(), "서연");
        var listed = friendDeskService.listCreated(creator.getId()).desks();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).messageCount()).isEqualTo(2);
        assertThat(listed.get(0).friendCount()).isEqualTo(2);
        // 만든 사람은 주인이 아니므로 받은 응원 목록 API(주인 전용)로는 이 책상이 열리지 않는다.
        assertThatThrownBy(() -> deskService.getMyDesk(creator.getId()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void claimRequiresConfirmationAndTransfersOwnership() {
        UserAccount creator = user("만든이");
        UserAccount recipient = user("지수");
        FriendDeskResponse desk = friendDeskService.create(creator, deskRequest("지수"));
        leaveCheer(desk.supporterToken(), "민준");

        var preview = friendDeskService.preview(desk.claimToken());
        assertThat(preview.displayName()).isEqualTo("지수");
        assertThat(preview.creatorName()).isEqualTo("만든이");
        assertThat(preview.messageCount()).isEqualTo(1);

        assertThatThrownBy(() -> friendDeskService.claim(recipient, desk.claimToken(), false))
                .extracting("errorCode").isEqualTo(ErrorCode.CLAIM_CONFIRMATION_REQUIRED);
        assertThat(deskRepository.findByClaimToken(desk.claimToken())).isPresent();

        var result = friendDeskService.claim(recipient, desk.claimToken(), true);
        assertThat(result.messageCount()).isEqualTo(1);
        assertThat(deskService.getMyDesk(recipient.getId()).id()).isEqualTo(desk.id());

        PersonalDesk saved = deskRepository.findById(desk.id()).orElseThrow();
        assertThat(saved.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(saved.getClaimToken()).isNull();
        assertThat(saved.getCreator().getId()).isEqualTo(creator.getId());
        // 링크는 한 번만 쓸 수 있고, 만든 사람의 목록에서도 사라진다.
        assertThatThrownBy(() -> friendDeskService.preview(desk.claimToken()))
                .extracting("errorCode").isEqualTo(ErrorCode.CLAIM_NOT_AVAILABLE);
        assertThat(friendDeskService.listCreated(creator.getId()).desks()).isEmpty();
        // 넘겨받은 뒤에는 만든 사람이 지울 수 없다.
        assertThatThrownBy(() -> friendDeskService.delete(creator.getId(), desk.id()))
                .extracting("errorCode").isEqualTo(ErrorCode.DESK_NOT_FOUND);
    }

    @Test
    void creatorMayClaimOwnDeskButNobodyWithADeskCan() {
        UserAccount creator = user("만든이");
        var mine = friendDeskService.create(creator, deskRequest("나"));
        friendDeskService.claim(creator, mine.claimToken(), true);
        assertThat(deskService.getMyDesk(creator.getId()).id()).isEqualTo(mine.id());

        var other = friendDeskService.create(user("다른이"), deskRequest("친구"));
        assertThatThrownBy(() -> friendDeskService.claim(creator, other.claimToken(), true))
                .extracting("errorCode").isEqualTo(ErrorCode.DESK_ALREADY_EXISTS);
        assertThat(deskRepository.findByClaimToken(other.claimToken())).isPresent();
    }

    @Test
    void onlyOneOfManyConcurrentClaimsSucceeds() throws Exception {
        FriendDeskResponse desk = friendDeskService.create(user("만든이"), deskRequest("지수"));
        int attempts = 6;
        List<UserAccount> claimers = new ArrayList<>();
        for (int i = 0; i < attempts; i++) claimers.add(user("수령자" + i));

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UserAccount claimer : claimers) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    friendDeskService.claim(claimer, desk.claimToken(), true);
                    return true;
                } catch (CustomException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) if (result.get()) succeeded++;
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(deskRepository.findById(desk.id()).orElseThrow().getOwner()).isNotNull();
    }

    @Test
    void currentOwnerCanDeleteAndTheDeskBecomesUnreachable() {
        UserAccount creator = user("만든이");
        FriendDeskResponse desk = friendDeskService.create(creator, deskRequest("지수"));
        UserAccount stranger = user("남");
        assertThatThrownBy(() -> friendDeskService.delete(stranger.getId(), desk.id()))
                .extracting("errorCode").isEqualTo(ErrorCode.DESK_NOT_FOUND);

        friendDeskService.delete(creator.getId(), desk.id());
        assertThatThrownBy(() -> friendDeskService.preview(desk.claimToken()))
                .extracting("errorCode").isEqualTo(ErrorCode.CLAIM_NOT_AVAILABLE);
        assertThatThrownBy(() -> deskService.getPublicDesk(desk.supporterToken()))
                .extracting("errorCode").isEqualTo(ErrorCode.DESK_NOT_FOUND);
        assertThatThrownBy(() -> leaveCheer(desk.supporterToken(), "늦은친구"))
                .extracting("errorCode").isEqualTo(ErrorCode.DESK_NOT_FOUND);
        assertThat(friendDeskService.listCreated(creator.getId()).desks()).isEmpty();
    }

    @Test
    void ownerDeletingAClaimedDeskFreesThemToMakeAnother() {
        UserAccount creator = user("만든이");
        UserAccount recipient = user("지수");
        FriendDeskResponse desk = friendDeskService.create(creator, deskRequest("지수"));
        friendDeskService.claim(recipient, desk.claimToken(), true);

        friendDeskService.delete(recipient.getId(), desk.id());
        assertThatThrownBy(() -> deskService.getMyDesk(recipient.getId()))
                .isInstanceOf(CustomException.class);
        assertThat(deskService.createMyDesk(recipient, deskRequest("새 책상")).id()).isNotEqualTo(desk.id());
    }

    @Test
    void creatorCannotPileUpUnclaimedDesks() {
        UserAccount creator = user("만든이");
        for (int i = 0; i < FriendDeskService.MAX_UNCLAIMED_PER_CREATOR; i++) {
            friendDeskService.create(creator, deskRequest("친구" + i));
        }
        assertThatThrownBy(() -> friendDeskService.create(creator, deskRequest("하나 더")))
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_FRIEND_DESKS);
    }
}
