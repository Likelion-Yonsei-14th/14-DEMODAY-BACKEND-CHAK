package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdminDtos.UserDetail;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.MessageResponse;
import com.likelion.chak.dto.OAuthLoginRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.RefreshTokenRepository;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AdminUserServiceTest {

    @Autowired private AdminUserService adminUserService;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private PersonalDeskRepository deskRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private DeskService deskService;
    @Autowired private MessageService messageService;
    @Autowired private TokenService tokenService;
    @Autowired private AuthService authService;
    @Autowired private com.likelion.chak.repository.ChaltteokWalletRepository walletRepository;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GoogleClient googleClient;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, name + "@x.com", "https://img/p.png"));
    }

    private UserAccount admin() {
        UserAccount admin = user("운영자");
        admin.updateAdmin(true);
        return userRepository.save(admin);
    }

    private PersonalDesk desk(UserAccount owner) {
        return deskService.createClaimedDesk(owner, owner.getDisplayName(), ReadModeType.TIME_CAPSULE, null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private MessageResponse send(String supporterToken, UserAccount author) {
        GuestMessageCreateRequest request = objectMapper.readValue("""
                {"nickname":"실명 닉네임","kind":"CARD","visibility":"PUBLIC","schemaVersion":1,
                 "card":{"pages":[{"id":"p1","elements":[]}]},
                 "object":{"representationType":"LETTER","assetId":"letter-basic","color":"cream","x":50,"y":50,"rotation":0,"scale":1,"zIndex":1,"metadata":{}}}""",
                GuestMessageCreateRequest.class);
        return messageService.createUserMessage(supporterToken, author, request);
    }

    @Test
    void withdrawAnonymizesTheAccountButKeepsItsLettersAsWithdrawnUser() {
        UserAccount admin = admin();
        UserAccount owner = user("받는 사람");
        UserAccount author = user("김철수");
        PersonalDesk ownerDesk = desk(owner);
        MessageResponse letter = send(ownerDesk.getSupporterToken(), author);
        tokenService.issue(author);
        assertThat(refreshTokenRepository.count()).isPositive();

        UserDetail detail = adminUserService.withdraw(admin.getId(), author.getId(), false);

        UserAccount after = userRepository.findById(author.getId()).orElseThrow();
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(after.getEmail()).isNull();
        assertThat(after.getProfileImageUrl()).isNull();
        assertThat(after.getProviderUserId()).isEqualTo("deleted:" + author.getId());
        assertThat(detail.user().deletedAt()).isNotNull();
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getUser().getId().equals(author.getId()))).isEmpty();
        // 응원은 남고 작성자 표시만 익명으로 바뀐다
        assertThat(messageService.getPublicMessages(ownerDesk.getSupporterToken()))
                .extracting(MessageResponse::getAuthorDisplayName).containsExactly("탈퇴한 사용자");
        assertThat(messageService.getPublicMessages(ownerDesk.getSupporterToken()).get(0).getId()).isEqualTo(letter.getId());
    }

    @Test
    void withdrawingAnOwnerClosesTheDeskAndHidesTheOwnerName() {
        UserAccount admin = admin();
        UserAccount owner = user("지수");
        PersonalDesk ownerDesk = desk(owner);

        adminUserService.withdraw(admin.getId(), owner.getId(), false);

        PersonalDesk after = deskRepository.findById(ownerDesk.getId()).orElseThrow();
        assertThat(after.isRoomClosed()).isTrue();
        assertThat(after.getDisplayName()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    void withdrawingIsIdempotentAndAllowsSigningUpAgainWithTheSameLogin() {
        UserAccount admin = admin();
        String googleId = "g-" + UUID.randomUUID();
        UserAccount original = userRepository.save(UserAccount.createGoogle(googleId, "원래 이름", "a@b.com", null));

        adminUserService.withdraw(admin.getId(), original.getId(), false);
        UserDetail again = adminUserService.withdraw(admin.getId(), original.getId(), false);
        assertThat(again.user().deletedAt()).isNotNull();

        when(googleClient.provider()).thenReturn(AuthProvider.GOOGLE);
        when(googleClient.authenticate(any(), any())).thenReturn(
                new OAuthProfile(AuthProvider.GOOGLE, googleId, "새 이름", "a@b.com", null));
        var login = authService.loginWithGoogle(new OAuthLoginRequest("code", "http://localhost/cb"));

        assertThat(login.user().id()).isNotEqualTo(original.getId());
        assertThat(login.user().displayName()).isEqualTo("새 이름");
    }

    @Test
    void remainingChaltteokNeedsExplicitConfirmation() {
        UserAccount admin = admin();
        UserAccount member = user("충전한 회원");
        var wallet = com.likelion.chak.domain.ChaltteokWallet.create(member);
        wallet.credit(500);
        walletRepository.save(wallet);

        assertThatThrownBy(() -> adminUserService.withdraw(admin.getId(), member.getId(), false))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BALANCE_REMAINING));
        assertThat(userRepository.findById(member.getId()).orElseThrow().isDeleted()).isFalse();

        UserDetail done = adminUserService.withdraw(admin.getId(), member.getId(), true);
        assertThat(done.user().deletedAt()).isNotNull();
        assertThat(done.history()).anyMatch(h -> "WITHDRAW_USER".equals(h.action()) && h.detail().contains("500"));
    }

    @Test
    void adminAccountsAndSelfCannotBeWithdrawn() {
        UserAccount admin = admin();
        UserAccount otherAdmin = admin();

        assertThatThrownBy(() -> adminUserService.withdraw(admin.getId(), otherAdmin.getId(), false))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED));
        assertThatThrownBy(() -> adminUserService.withdraw(admin.getId(), admin.getId(), false))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void listSearchesByNameEmailAndIdAndFiltersByState() {
        UserAccount admin = admin();
        UserAccount kim = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "검색대상김", "findme@x.com", null));
        UserAccount gone = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "검색대상탈퇴", null, null));
        adminUserService.withdraw(admin.getId(), gone.getId(), false);

        assertThat(adminUserService.list("검색대상", "ACTIVE", 0, 20).content())
                .extracting(u -> u.id()).contains(kim.getId()).doesNotContain(gone.getId());
        assertThat(adminUserService.list("findme", "ACTIVE", 0, 20).content())
                .extracting(u -> u.id()).containsExactly(kim.getId());
        assertThat(adminUserService.list(String.valueOf(kim.getId()), "ALL", 0, 20).content())
                .extracting(u -> u.id()).contains(kim.getId());
        assertThat(adminUserService.list("", "DELETED", 0, 100).content())
                .extracting(u -> u.id()).contains(gone.getId());
    }

    @Test
    void detailShowsDeskAndMessageCounts() {
        UserAccount owner = user("받는 사람");
        UserAccount author = user("보내는 사람");
        PersonalDesk ownerDesk = desk(owner);
        send(ownerDesk.getSupporterToken(), author);
        send(ownerDesk.getSupporterToken(), author);

        UserDetail ownerDetail = adminUserService.detail(owner.getId());
        UserDetail authorDetail = adminUserService.detail(author.getId());

        assertThat(ownerDetail.receivedMessages()).isEqualTo(2);
        assertThat(ownerDetail.deskId()).isEqualTo(ownerDesk.getId());
        assertThat(authorDetail.sentMessages()).isEqualTo(2);
        assertThat(authorDetail.deskId()).isNull();
    }
}
