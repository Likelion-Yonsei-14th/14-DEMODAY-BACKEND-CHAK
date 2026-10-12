package com.likelion.chak.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {

    public AuthTokenResponse withoutRefreshToken() {
        return new AuthTokenResponse(accessToken, null, tokenType, expiresIn, user);
    }
}
