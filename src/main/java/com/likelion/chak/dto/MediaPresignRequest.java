package com.likelion.chak.dto;

import com.likelion.chak.domain.MediaPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MediaPresignRequest(
        @NotNull MediaPurpose purpose,
        @NotBlank @Size(max = 255) String originalFileName,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long size) {
}
