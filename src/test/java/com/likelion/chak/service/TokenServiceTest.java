package com.likelion.chak.service;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void issuesAccessAndRefreshTokens() {
        UserAccount user = saveUser("kakao-token-1");

        AuthTokenResponse response = tokenService.issue(user);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(jwtTokenProvider.getUserId(response.accessToken())).isEqualTo(user.getId());
    }

    @Test
    void refreshTokenCanOnlyBeUsedOnce() {
        UserAccount user = saveUser("kakao-token-2");
        AuthTokenResponse first = tokenService.issue(user);

        AuthTokenResponse rotated = tokenService.rotate(first.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> tokenService.rotate(first.refreshToken()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void revokedRefreshTokenCannotBeUsed() {
        UserAccount user = saveUser("kakao-token-3");
        AuthTokenResponse issued = tokenService.issue(user);

        tokenService.revoke(user.getId(), issued.refreshToken());

        assertThatThrownBy(() -> tokenService.rotate(issued.refreshToken()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private UserAccount saveUser(String kakaoId) {
        return userAccountRepository.save(
                UserAccount.createKakao(kakaoId, "지수", null, null));
    }
}
