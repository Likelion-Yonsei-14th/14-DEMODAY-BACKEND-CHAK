package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.MinigameDtos.NicknameRequest;
import com.likelion.chak.dto.MinigameDtos.NicknameResponse;
import com.likelion.chak.service.MinigameNicknameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 미니게임 랭킹 닉네임: 로그인 회원만. */
@RestController
@RequestMapping("/api/minigames/me/nickname")
@RequiredArgsConstructor
public class MinigameNicknameController {

    private final MinigameNicknameService service;

    @Operation(summary = "미니게임 닉네임 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<NicknameResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.get(user.userId()));
    }

    @Operation(summary = "미니게임 닉네임 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping
    public ResponseEntity<NicknameResponse> set(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody NicknameRequest request) {
        return ResponseEntity.ok(service.set(user.userId(), request.nickname()));
    }
}
