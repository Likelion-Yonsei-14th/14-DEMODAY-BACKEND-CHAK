package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.KakaoLoginRequest;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.RefreshTokenRequest;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @Operation(summary = "카카오 로그인", description = "프론트가 받은 카카오 인가 코드를 서비스 토큰으로 교환합니다.")
    @PostMapping("/kakao")
    public ResponseEntity<AuthTokenResponse> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithKakao(request));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰을 회전하고 새 토큰 쌍을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(tokenService.rotate(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<MessageResponseBody> logout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(user.userId(), request.refreshToken());
        return ResponseEntity.ok(new MessageResponseBody("로그아웃되었습니다."));
    }
}
