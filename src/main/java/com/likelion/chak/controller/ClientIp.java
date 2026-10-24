package com.likelion.chak.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 접속자 구분용 IP. Vercel·CloudFront 뒤에서는 가장 왼쪽 X-Forwarded-For가 실제 접속자다.
 * 호출 횟수 제한에만 쓰고 저장하지 않는다. 헤더는 직접 호출하는 사람이 바꿀 수 있어 보안 경계가 아니라 남용을 줄이는 용도다.
 */
final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
