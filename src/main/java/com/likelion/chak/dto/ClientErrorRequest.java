package com.likelion.chak.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 브라우저에서 발생한 오류 보고. 모든 값은 서버가 한 번 더 정리해서 쓴다. */
public record ClientErrorRequest(
        @NotBlank @Pattern(regexp = "^(js_error|unhandled_rejection|render_error|api_error|network_error)$") String kind,
        @NotBlank @Size(max = 400) String message,
        @Size(max = 300) String route,
        @Size(max = 300) String apiPath,
        @Min(0) @Max(999) Integer status,
        @Size(max = 64) String code,
        @Size(max = 2000) String stack,
        @Size(max = 40) String buildId,
        @Size(max = 40) String reportId) {
}
