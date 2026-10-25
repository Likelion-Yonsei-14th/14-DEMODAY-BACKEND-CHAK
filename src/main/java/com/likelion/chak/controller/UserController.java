package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.ProfileDtos.AvatarRequest;
import com.likelion.chak.dto.ProfileDtos.DisplayNameRequest;
import com.likelion.chak.dto.UserResponse;
import com.likelion.chak.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profileService;

    @Operation(summary = "내 정보 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(profileService.me(user.userId()));
    }

    @Operation(summary = "내 이름 바꾸기", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> changeName(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DisplayNameRequest request) {
        return ResponseEntity.ok(profileService.changeName(user.userId(), request.displayName()));
    }

    @Operation(summary = "프로필 사진 올리기", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/me/avatar")
    public ResponseEntity<UserResponse> setAvatar(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AvatarRequest request) {
        return ResponseEntity.ok(profileService.setAvatar(user.userId(), request.image()));
    }

    @Operation(summary = "프로필 사진 지우기", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> removeAvatar(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(profileService.removeAvatar(user.userId()));
    }
}
