package com.likelion.chak.dto;

import com.likelion.chak.domain.UserAccount;

public record UserResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl());
    }
}
