package com.likelion.chak.service;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.RefreshToken;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.UserResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    @Transactional
    public AuthTokenResponse issue(UserAccount user) {
        String rawRefreshToken = createRandomToken();
        refreshTokenRepository.save(RefreshToken.create(
                user,
                hash(rawRefreshToken),
                Instant.now().plusSeconds(refreshTokenExpirationSeconds)));

        return response(user, rawRefreshToken);
    }

    @Transactional
    public AuthTokenResponse rotate(String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        Instant now = Instant.now();
        if (storedToken.isRevoked()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (storedToken.isExpired(now)) {
            storedToken.revoke(now);
            throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        storedToken.revoke(now);
        return issue(storedToken.getUser());
    }

    @Transactional
    public void revoke(Long userId, String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!storedToken.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        storedToken.revoke(Instant.now());
    }

    private AuthTokenResponse response(UserAccount user, String refreshToken) {
        return new AuthTokenResponse(
                jwtTokenProvider.createAccessToken(user),
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                UserResponse.from(user));
    }

    private String createRandomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
