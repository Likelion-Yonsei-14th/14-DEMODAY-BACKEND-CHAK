package com.likelion.chak.service;

import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.KakaoLoginRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoClient kakaoClient;
    private final UserAccountRepository userAccountRepository;
    private final TokenService tokenService;

    @Transactional
    public AuthTokenResponse loginWithKakao(KakaoLoginRequest request) {
        KakaoProfile profile = kakaoClient.authenticate(request.code(), request.redirectUri());
        UserAccount user = userAccountRepository.findByKakaoId(profile.kakaoId())
                .map(existing -> updateProfile(existing, profile))
                .orElseGet(() -> userAccountRepository.save(UserAccount.createKakao(
                        profile.kakaoId(),
                        profile.nickname(),
                        profile.email(),
                        profile.profileImageUrl())));

        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private UserAccount updateProfile(UserAccount user, KakaoProfile profile) {
        user.updateKakaoProfile(profile.nickname(), profile.email(), profile.profileImageUrl());
        return user;
    }
}
