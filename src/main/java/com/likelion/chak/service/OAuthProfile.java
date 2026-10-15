package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;

/** 제공자에서 받아온 사용자 정보. providerUserId는 제공자가 보장하는 불변 식별자다. */
public record OAuthProfile(
        AuthProvider provider,
        String providerUserId,
        String nickname,
        String email,
        String profileImageUrl) {
}
