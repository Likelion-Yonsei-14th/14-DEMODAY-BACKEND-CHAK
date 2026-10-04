package com.likelion.chak.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    DESK_NOT_FOUND(HttpStatus.NOT_FOUND, "DESK_NOT_FOUND", "응원 책상을 찾을 수 없습니다."),
    DESK_CLOSED(HttpStatus.CONFLICT, "DESK_CLOSED", "이 책상은 새로운 응원을 받고 있지 않습니다."),
    PUBLIC_FEED_DISABLED(HttpStatus.FORBIDDEN, "PUBLIC_FEED_DISABLED", "공개 응원 보기가 비활성화되어 있습니다."),
    GUEST_NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "GUEST_NICKNAME_REQUIRED", "비회원은 닉네임을 입력해야 합니다."),
    INVALID_MESSAGE_KIND(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_KIND", "지원하지 않는 응원 종류입니다."),
    INVALID_MESSAGE_VISIBILITY(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_VISIBILITY", "공개 범위를 선택해야 합니다."),
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
