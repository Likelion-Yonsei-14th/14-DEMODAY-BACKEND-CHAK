package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertMessage;
import com.likelion.chak.alert.AlertSanitizer;
import com.likelion.chak.alert.BrowserLabel;
import com.likelion.chak.alert.ClientErrorRateLimiter;
import com.likelion.chak.alert.ErrorAlertService;
import com.likelion.chak.dto.ClientErrorRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 브라우저에서 난 오류를 운영자에게 알린다. 로그인 없이 호출하며, 접수한 내용은 저장하지 않고 알림으로만 보낸다. */
@RestController
@RequestMapping("/api/public/client-errors")
@RequiredArgsConstructor
public class ClientErrorController {

    private final ErrorAlertService alertService;
    private final ClientErrorRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<Void> report(
            @Valid @RequestBody ClientErrorRequest request, HttpServletRequest http) {
        String userAgent = http.getHeader("User-Agent");
        if (!rateLimiter.tryAcquire(clientIp(http), userAgent)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }

        String message = AlertSanitizer.clean(request.message(), 300);
        String route = AlertSanitizer.path(request.route(), 120);
        String apiPath = AlertSanitizer.path(request.apiPath(), 120);
        String fingerprint = "client|" + request.kind() + "|" + route + "|" + apiPath + "|" + request.status()
                + "|" + request.code() + "|" + AlertSanitizer.truncate(message, 80);

        alertService.alert(AlertMessage.builder("프론트", titleOf(request), fingerprint, severityOf(request))
                .description(message)
                .field("화면", route)
                .field("요청", apiPath)
                .field("상태", request.status() == null || request.status() == 0 ? null : String.valueOf(request.status()))
                .field("코드", AlertSanitizer.clean(request.code(), 64))
                .field("브라우저", BrowserLabel.of(userAgent))
                .field("빌드", AlertSanitizer.clean(request.buildId(), 40))
                .field("접수 코드", AlertSanitizer.clean(request.reportId(), 40))
                .field("스택", AlertSanitizer.clean(request.stack(), 700))
                .build());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private static String titleOf(ClientErrorRequest request) {
        return switch (request.kind()) {
            case "api_error" -> "API 오류" + (request.status() != null && request.status() > 0 ? " " + request.status() : "");
            case "network_error" -> "네트워크 오류";
            case "render_error" -> "화면 렌더링 오류";
            case "unhandled_rejection" -> "처리되지 않은 비동기 오류";
            default -> "자바스크립트 오류";
        };
    }

    private static AlertMessage.Severity severityOf(ClientErrorRequest request) {
        boolean serverSide = request.status() != null && request.status() >= 500;
        return serverSide || "render_error".equals(request.kind())
                ? AlertMessage.Severity.ERROR : AlertMessage.Severity.WARN;
    }

    /** Vercel·CloudFront 뒤에서는 가장 왼쪽 X-Forwarded-For가 실제 접속자다. 제한용으로만 쓰고 저장하지 않는다. */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
