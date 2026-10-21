package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.OAuthLoginRequest;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.likelion.chak.repository.AdminInviteRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired private AuthService authService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private AdminAccessService adminAccessService;
    @Autowired private AdminInviteRepository inviteRepository;
    @MockitoBean private GoogleClient googleClient;
    @MockitoBean private KakaoClient kakaoClient;

    private static final OAuthLoginRequest REQUEST = new OAuthLoginRequest("code", "http://localhost/cb");

    private void google(String sub, String name, String email) {
        when(googleClient.provider()).thenReturn(AuthProvider.GOOGLE);
        when(googleClient.authenticate(any(), any()))
                .thenReturn(new OAuthProfile(AuthProvider.GOOGLE, sub, name, email, null));
    }

    @Test
    void firstGoogleLoginCreatesAccountAndLaterLoginsReuseIt() {
        google("g-1", "홍길동", "a@b.com");
        var first = authService.loginWithGoogle(REQUEST);
        google("g-1", "새 이름", "a@b.com");
        var second = authService.loginWithGoogle(REQUEST);

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(second.user().displayName()).isEqualTo("새 이름");
        assertThat(second.user().provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(userAccountRepository.findAll().stream()
                .filter(u -> u.getProviderUserId().equals("g-1"))).hasSize(1);
    }

    @Test
    void sameIdentifierUnderDifferentProvidersAreDifferentAccounts() {
        UserAccount kakao = userAccountRepository.save(UserAccount.createKakao("same-id", "카카오", null, null));
        google("same-id", "구글", null);
        var login = authService.loginWithGoogle(REQUEST);
        assertThat(login.user().id()).isNotEqualTo(kakao.getId());
    }

    @Test
    void sameEmailUnderDifferentProvidersIsNotMerged() {
        UserAccount kakao = userAccountRepository.save(UserAccount.createKakao("k-9", "카카오", "x@y.com", null));
        google("g-9", "구글", "x@y.com");
        assertThat(authService.loginWithGoogle(REQUEST).user().id()).isNotEqualTo(kakao.getId());
    }

    @Test
    void inviteMakesGoogleUserAdminCaseInsensitivelyAndIsConsumedOnce() {
        inviteRepository.save(new com.likelion.chak.domain.AdminInvite("mj0324@yonsei.ac.kr", null));
        google("g-adm", "운영자", "Mj0324@Yonsei.ac.kr");
        var login = authService.loginWithGoogle(REQUEST);
        assertThat(login.user().admin()).isTrue();
        assertThat(inviteRepository.findByEmail("mj0324@yonsei.ac.kr").orElseThrow().isConsumed()).isTrue();
    }

    @Test
    void inviteIsNotAppliedToKakaoAccountsWithSameEmail() {
        inviteRepository.save(new com.likelion.chak.domain.AdminInvite("mj0324@yonsei.ac.kr", null));
        when(kakaoClient.provider()).thenReturn(AuthProvider.KAKAO);
        when(kakaoClient.authenticate(any(), any()))
                .thenReturn(new OAuthProfile(AuthProvider.KAKAO, "k-adm", "카카오", "mj0324@yonsei.ac.kr", null));
        assertThat(authService.loginWithKakao(REQUEST).user().admin()).isFalse();
        assertThat(inviteRepository.findByEmail("mj0324@yonsei.ac.kr").orElseThrow().isConsumed()).isFalse();
    }

    @Test
    void grantedAdminSurvivesLaterLogins() {
        google("g-keep", "회원", "keep@b.com");
        var first = authService.loginWithGoogle(REQUEST);
        UserAccount boss = userAccountRepository.save(UserAccount.createKakao("k-boss", "운영", null, null));
        adminAccessService.grant(boss.getId(), "KEEP@b.com");
        assertThat(authService.loginWithGoogle(REQUEST).user().admin()).isTrue();
        assertThat(userAccountRepository.findById(first.user().id()).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void grantForUnknownEmailCreatesInviteAppliedOnFirstLoginAndCanBeCancelled() {
        UserAccount boss = userAccountRepository.save(UserAccount.createKakao("k-boss2", "운영", null, null));
        var result = adminAccessService.grant(boss.getId(), " New@B.com ");
        assertThat(result.invites()).anyMatch(i -> i.email().equals("new@b.com"));
        adminAccessService.grant(boss.getId(), "new@b.com");
        assertThat(inviteRepository.findAll().stream().filter(i -> i.getEmail().equals("new@b.com"))).hasSize(1);

        google("g-new", "신규", "new@b.com");
        assertThat(authService.loginWithGoogle(REQUEST).user().admin()).isTrue();

        adminAccessService.grant(boss.getId(), "other@b.com");
        Long id = inviteRepository.findByEmail("other@b.com").orElseThrow().getId();
        adminAccessService.cancelInvite(boss.getId(), id);
        assertThat(inviteRepository.findByEmail("other@b.com")).isEmpty();
    }

    @Test
    void revokeBlocksSelfAndNonAdminsAndRemovesRights() {
        UserAccount boss = userAccountRepository.save(UserAccount.createKakao("k-boss3", "운영", null, null));
        boss.updateAdmin(true);
        UserAccount other = userAccountRepository.save(UserAccount.createKakao("k-o", "다른운영", null, null));
        other.updateAdmin(true);
        UserAccount member = userAccountRepository.save(UserAccount.createKakao("k-m", "회원", null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminAccessService.revoke(boss.getId(), boss.getId()))
                .hasMessageContaining("본인");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminAccessService.revoke(boss.getId(), member.getId()))
                .isInstanceOf(com.likelion.chak.exception.CustomException.class);
        adminAccessService.revoke(boss.getId(), other.getId());
        assertThat(userAccountRepository.findById(other.getId()).orElseThrow().isAdmin()).isFalse();
    }

    @Test
    void invalidEmailIsRejected() {
        UserAccount boss = userAccountRepository.save(UserAccount.createKakao("k-boss4", "운영", null, null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminAccessService.grant(boss.getId(), "not-an-email"))
                .isInstanceOf(com.likelion.chak.exception.CustomException.class);
    }
}
