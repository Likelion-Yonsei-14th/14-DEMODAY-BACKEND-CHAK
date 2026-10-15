package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.config.RefreshCookieService;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.OAuthLoginRequest;
import com.likelion.chak.dto.RefreshTokenRequest;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final RefreshCookieService refreshCookieService;

    @Operation(summary = "카카오 로그인", description = "프론트가 받은 카카오 인가 코드를 서비스 토큰으로 교환합니다. 리프레시 토큰은 HttpOnly 쿠키로도 내려갑니다.")
    @PostMapping("/kakao")
    public ResponseEntity<AuthTokenResponse> loginWithKakao(
            @Valid @RequestBody OAuthLoginRequest request) {
        return tokenResponse(authService.loginWithKakao(request));
    }

    @Operation(summary = "구글 로그인", description = "프론트가 받은 구글 인가 코드를 서비스 토큰으로 교환합니다. 리프레시 토큰은 HttpOnly 쿠키로도 내려갑니다.")
    @PostMapping("/google")
    public ResponseEntity<AuthTokenResponse> loginWithGoogle(
            @Valid @RequestBody OAuthLoginRequest request) {
        return tokenResponse(authService.loginWithGoogle(request));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰(본문 또는 쿠키)을 회전하고 새 토큰 쌍을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            HttpServletRequest httpRequest,
            @RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = refreshCookieService.resolve(
                httpRequest, request == null ? null : request.refreshToken());
        return tokenResponse(tokenService.rotate(refreshToken));
    }

    @Operation(summary = "로그아웃", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<MessageResponseBody> logout(
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = refreshCookieService.resolve(
                httpRequest, request == null ? null : request.refreshToken());
        tokenService.revoke(user.userId(), refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.clearHeader())
                .body(new MessageResponseBody("로그아웃되었습니다."));
    }

    private ResponseEntity<AuthTokenResponse> tokenResponse(AuthTokenResponse tokens) {
        String cookie = refreshCookieService.issueHeader(tokens.refreshToken());
        AuthTokenResponse body = refreshCookieService.isIncludeInBody()
                ? tokens
                : tokens.withoutRefreshToken();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(body);
    }
}
