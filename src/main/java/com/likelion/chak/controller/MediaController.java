package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MediaAssetResponse;
import com.likelion.chak.dto.MediaDownloadResponse;
import com.likelion.chak.dto.MediaPresignRequest;
import com.likelion.chak.dto.MediaPresignResponse;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private final AuthService authService;
    private final MediaService mediaService;

    @Operation(summary = "이미지 업로드 URL 발급")
    @PostMapping("/presign")
    public ResponseEntity<MediaPresignResponse> presign(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody MediaPresignRequest request) {
        UserAccount user = authService.getUser(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.createUpload(user, request));
    }

    @Operation(summary = "이미지 업로드 완료")
    @PostMapping("/{assetId}/complete")
    public ResponseEntity<MediaAssetResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("assetId") Long assetId) {
        return ResponseEntity.ok(mediaService.complete(principal.userId(), assetId));
    }

    @Operation(summary = "비공개 이미지 다운로드 URL 발급")
    @GetMapping("/{assetId}/download-url")
    public ResponseEntity<MediaDownloadResponse> downloadUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("assetId") Long assetId) {
        return ResponseEntity.ok(mediaService.createDownload(principal.userId(), assetId));
    }
}
