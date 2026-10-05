package com.likelion.chak.dto;

import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.ReadModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;

public record DeskSettingsRequest(
        @NotBlank @Size(max = 50) String displayName,
        @NotNull ReadModeType readModeType,
        LocalTime dailyUnlockTime,
        Instant capsuleUnlockAt,
        boolean publicFeedEnabled,
        @NotNull MessageVisibility defaultMessageVisibility,
        boolean applyVisibilityToExisting) {
}
