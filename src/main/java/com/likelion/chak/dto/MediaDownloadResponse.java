package com.likelion.chak.dto;

import java.time.Instant;

public record MediaDownloadResponse(String downloadUrl, Instant expiresAt) {
}
