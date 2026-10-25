package com.likelion.chak.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** 표시 이름. 길이와 문자는 서비스에서 앞뒤 공백을 지운 값으로 다시 확인한다. */
    public record DisplayNameRequest(@NotBlank @Size(max = 60) String displayName) {
    }

    /** {@code data:image/jpeg;base64,…} 형태의 작은 사진(JPEG·PNG·WebP, 풀어서 150KB 이하). */
    public record AvatarRequest(@NotBlank @Size(max = 260_000) String image) {
    }
}
