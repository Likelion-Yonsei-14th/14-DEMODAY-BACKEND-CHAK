package com.likelion.chak.dto;

import java.time.Instant;
import java.util.Map;

public record MediaPresignResponse(
        Long assetId,
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt) {
}
