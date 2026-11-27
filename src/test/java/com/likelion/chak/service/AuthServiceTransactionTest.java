package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.dto.OAuthLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 제공자 HTTP 호출 중에는 DB 트랜잭션(=연결)을 잡고 있지 않아야 한다(F27). */
@SpringBootTest
class AuthServiceTransactionTest {

    @Autowired private AuthService authService;
    @MockitoBean private GoogleClient googleClient;
    @MockitoBean private KakaoClient kakaoClient;

    @Test
    void providerCallRunsOutsideAnyTransactionAndTheAccountIsStillSaved() {
        AtomicBoolean transactionDuringCall = new AtomicBoolean(true);
        String sub = "g-" + UUID.randomUUID();
        when(googleClient.provider()).thenReturn(AuthProvider.GOOGLE);
        when(googleClient.authenticate(any(), any())).thenAnswer(invocation -> {
            transactionDuringCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new OAuthProfile(AuthProvider.GOOGLE, sub, "이름", null, null);
        });

        var login = authService.loginWithGoogle(new OAuthLoginRequest("code", "http://localhost/cb"));

        assertThat(transactionDuringCall.get()).isFalse();
        assertThat(login.user().id()).isNotNull();
        assertThat(login.accessToken()).isNotBlank();
    }
}
