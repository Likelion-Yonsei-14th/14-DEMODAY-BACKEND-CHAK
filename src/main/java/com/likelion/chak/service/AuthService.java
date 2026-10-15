package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.OAuthLoginRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 소셜 로그인. 계정은 (제공자, 제공자 식별자)로만 찾는다.
 * 제공자가 달라도 이메일이 같다고 계정을 합치지 않는다(검증되지 않은 이메일로 남의 계정을 가로채는 것을 막기 위해).
 */
@Service
public class AuthService {

    private final List<OAuthClient> clients;
    private final UserAccountRepository userAccountRepository;
    private final TokenService tokenService;
    private final String adminKakaoIds;
    private final String adminGoogleIds;

    public AuthService(
            List<OAuthClient> oauthClients,
            UserAccountRepository userAccountRepository,
            TokenService tokenService,
            @Value("${chak.admin-kakao-ids:}") String adminKakaoIds,
            @Value("${chak.admin-google-ids:}") String adminGoogleIds) {
        this.clients = oauthClients;
        this.userAccountRepository = userAccountRepository;
        this.tokenService = tokenService;
        this.adminKakaoIds = adminKakaoIds;
        this.adminGoogleIds = adminGoogleIds;
    }

    @Transactional
    public AuthTokenResponse loginWithKakao(OAuthLoginRequest request) {
        return login(AuthProvider.KAKAO, request);
    }

    @Transactional
    public AuthTokenResponse loginWithGoogle(OAuthLoginRequest request) {
        return login(AuthProvider.GOOGLE, request);
    }

    private AuthTokenResponse login(AuthProvider provider, OAuthLoginRequest request) {
        OAuthClient client = clients.stream()
                .filter(candidate -> candidate.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        OAuthProfile profile = client.authenticate(request.code(), request.redirectUri());
        UserAccount user = userAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(existing -> updateProfile(existing, profile))
                .orElseGet(() -> userAccountRepository.save(UserAccount.create(
                        profile.provider(),
                        profile.providerUserId(),
                        profile.nickname(),
                        profile.email(),
                        profile.profileImageUrl())));

        user.updateAdmin(isConfiguredAdmin(profile));
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserAccount getAdmin(Long userId) {
        UserAccount user = getUser(userId);
        if (!user.isAdmin()) {
            throw new CustomException(ErrorCode.ADMIN_REQUIRED);
        }
        return user;
    }

    private UserAccount updateProfile(UserAccount user, OAuthProfile profile) {
        user.updateProfile(profile.nickname(), profile.email(), profile.profileImageUrl());
        return user;
    }

    private boolean isConfiguredAdmin(OAuthProfile profile) {
        String configured = profile.provider() == AuthProvider.KAKAO ? adminKakaoIds : adminGoogleIds;
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(id -> !id.isEmpty() && id.equals(profile.providerUserId()));
    }
}
