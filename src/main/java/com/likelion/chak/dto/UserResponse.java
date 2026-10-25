package com.likelion.chak.dto;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;

public record UserResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        boolean admin,
        AuthProvider provider,
        /** 직접 올린 프로필 사진 주소(같은 서버의 상대 경로). 올리지 않았으면 null. */
        String avatarUrl) {

    public static final String AVATAR_PATH = "/api/public/avatars/";

    public static UserResponse from(UserAccount user) {
        return from(user, null);
    }

    public static UserResponse from(UserAccount user, String avatarKey) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.isAdmin(),
                user.getProvider(),
                avatarKey == null ? null : AVATAR_PATH + avatarKey);
    }
}
