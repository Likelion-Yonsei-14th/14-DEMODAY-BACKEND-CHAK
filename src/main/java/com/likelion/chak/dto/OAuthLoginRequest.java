package com.likelion.chak.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 소셜 로그인 요청. redirectUri는 인가 요청에 쓴 값과 같아야 하며 제공자가 다시 검증한다. */
public record OAuthLoginRequest(
        @NotBlank @Size(max = 2000) String code,
        @NotBlank @Size(max = 2000) String redirectUri) {
}
