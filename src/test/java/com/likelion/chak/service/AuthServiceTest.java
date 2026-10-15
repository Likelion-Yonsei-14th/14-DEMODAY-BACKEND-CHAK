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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired private AuthService authService;
    @Autowired private UserAccountRepository userAccountRepository;
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
}
