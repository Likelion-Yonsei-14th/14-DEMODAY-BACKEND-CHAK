package com.likelion.chak.dto;

import com.likelion.chak.domain.MediaAsset;
import com.likelion.chak.domain.MediaPurpose;
import com.likelion.chak.domain.MediaStatus;

import java.time.Instant;

public record MediaAssetResponse(
        Long id,
        MediaPurpose purpose,
        MediaStatus status,
        String contentType,
        long size,
        Instant uploadedAt) {

    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId(),
                asset.getPurpose(),
                asset.getStatus(),
                asset.getContentType(),
                asset.getExpectedSize(),
                asset.getUploadedAt());
    }
}
