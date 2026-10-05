package com.likelion.chak.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "운영자 권한이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    INVALID_KAKAO_AUTH_CODE(HttpStatus.UNAUTHORIZED, "INVALID_KAKAO_AUTH_CODE", "카카오 인가 코드가 유효하지 않습니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "KAKAO_API_ERROR", "카카오 로그인 서버와 통신하지 못했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN", "리프레시 토큰이 만료되었습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다."),
    DESK_NOT_FOUND(HttpStatus.NOT_FOUND, "DESK_NOT_FOUND", "응원 책상을 찾을 수 없습니다."),
    DESK_ALREADY_EXISTS(HttpStatus.CONFLICT, "DESK_ALREADY_EXISTS", "이미 개인 응원 책상이 있습니다."),
    INVALID_DESK_SETTINGS(HttpStatus.BAD_REQUEST, "INVALID_DESK_SETTINGS", "책상 공개 시각 설정이 올바르지 않습니다."),
    DESK_CLOSED(HttpStatus.CONFLICT, "DESK_CLOSED", "이 책상은 새로운 응원을 받고 있지 않습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "편지를 찾을 수 없습니다."),
    MESSAGE_LOCKED(HttpStatus.CONFLICT, "MESSAGE_LOCKED", "아직 열어볼 수 없는 편지입니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "업로드 자산을 찾을 수 없습니다."),
    INVALID_MEDIA(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "업로드할 이미지 정보가 올바르지 않습니다."),
    MEDIA_UPLOAD_NOT_FOUND(HttpStatus.CONFLICT, "MEDIA_UPLOAD_NOT_FOUND", "Object Storage에서 업로드 파일을 확인할 수 없습니다."),
    STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Object Storage 설정이 필요합니다."),
    STORAGE_API_ERROR(HttpStatus.BAD_GATEWAY, "STORAGE_API_ERROR", "Object Storage와 통신하지 못했습니다."),
    ADVERTISEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ADVERTISEMENT_NOT_FOUND", "광고를 찾을 수 없습니다."),
    INVALID_ADVERTISEMENT(HttpStatus.BAD_REQUEST, "INVALID_ADVERTISEMENT", "광고 정보가 올바르지 않습니다."),
    INVALID_ANALYTICS_EVENT(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_EVENT", "분석 이벤트 정보가 올바르지 않습니다."),
    TOO_MANY_EVENTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_EVENTS", "이벤트 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    PUBLIC_FEED_DISABLED(HttpStatus.FORBIDDEN, "PUBLIC_FEED_DISABLED", "공개 응원 보기가 비활성화되어 있습니다."),
    GUEST_NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "GUEST_NICKNAME_REQUIRED", "비회원은 닉네임을 입력해야 합니다."),
    INVALID_MESSAGE_KIND(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_KIND", "지원하지 않는 응원 종류입니다."),
    INVALID_CARD_PAYLOAD(HttpStatus.BAD_REQUEST, "INVALID_CARD_PAYLOAD", "카드 데이터 형식이 올바르지 않습니다."),
    INVALID_DESK_OBJECT(HttpStatus.BAD_REQUEST, "INVALID_DESK_OBJECT", "책상 오브젝트 정보가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
