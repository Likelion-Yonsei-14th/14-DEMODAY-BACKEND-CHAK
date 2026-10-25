package com.likelion.chak.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "운영자 권한이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    INVALID_KAKAO_AUTH_CODE(HttpStatus.UNAUTHORIZED, "INVALID_KAKAO_AUTH_CODE", "카카오 인가 코드가 유효하지 않습니다."),
    INVALID_GOOGLE_AUTH_CODE(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_AUTH_CODE", "구글 인가 코드가 유효하지 않습니다."),
    GOOGLE_API_ERROR(HttpStatus.BAD_GATEWAY, "GOOGLE_API_ERROR", "구글 API 통신 또는 설정 오류입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "KAKAO_API_ERROR", "카카오 로그인 서버와 통신하지 못했습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN", "리프레시 토큰이 만료되었습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다."),
    DESK_NOT_FOUND(HttpStatus.NOT_FOUND, "DESK_NOT_FOUND", "응원 책상을 찾을 수 없습니다."),
    DESK_ALREADY_EXISTS(HttpStatus.CONFLICT, "DESK_ALREADY_EXISTS", "이미 개인 응원 책상이 있습니다."),
    CLAIM_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "CLAIM_NOT_AVAILABLE", "이미 사용했거나 없는 책상 링크입니다."),
    CLAIM_CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST, "CLAIM_CONFIRMATION_REQUIRED", "책상의 주인 본인인지 확인이 필요합니다."),
    TOO_MANY_FRIEND_DESKS(HttpStatus.CONFLICT, "TOO_MANY_FRIEND_DESKS", "아직 전달하지 않은 친구 책상이 너무 많습니다."),
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "상담 신청을 찾을 수 없습니다."),
    INVALID_DESK_SETTINGS(HttpStatus.BAD_REQUEST, "INVALID_DESK_SETTINGS", "책상 공개 시각 설정이 올바르지 않습니다."),
    DESK_CLOSED(HttpStatus.CONFLICT, "DESK_CLOSED", "이 책상은 새로운 응원을 받고 있지 않습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "편지를 찾을 수 없습니다."),
    MESSAGE_NOT_DELETABLE(HttpStatus.CONFLICT, "MESSAGE_NOT_DELETABLE", "이미 읽었거나 공개 편지가 아니어서 삭제할 수 없습니다."),
    IDENTITY_REQUIRED(HttpStatus.UNAUTHORIZED, "IDENTITY_REQUIRED", "작성자를 확인할 수 없습니다. 로그인하거나 같은 브라우저에서 다시 시도해주세요."),
    REPLY_ALREADY_EXISTS(HttpStatus.CONFLICT, "REPLY_ALREADY_EXISTS", "이미 답장을 보낸 편지가 포함되어 있습니다."),
    INVALID_REPLY(HttpStatus.BAD_REQUEST, "INVALID_REPLY", "답장 대상이나 내용이 올바르지 않습니다."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "숨긴 작성자를 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다."),
    INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_CHARGE_AMOUNT", "충전할 수 없는 금액입니다. 300원 이상 50,000원 이하의 정수만 가능합니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "결제 금액이 주문과 다릅니다."),
    INVALID_PURCHASE(HttpStatus.BAD_REQUEST, "INVALID_PURCHASE", "구매 항목이 올바르지 않습니다."),
    INSUFFICIENT_CHALTTEOK(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_CHALTTEOK", "찰떡이 부족합니다."),
    PAYMENT_DECLINED(HttpStatus.BAD_REQUEST, "PAYMENT_DECLINED", "결제가 승인되지 않았습니다."),
    PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_CONFIRM_FAILED", "결제 승인 서버와 통신하지 못했습니다. 잠시 후 같은 결제를 다시 확인해주세요."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "ORDER_EXPIRED", "충전 주문이 만료되었습니다. 처음부터 다시 충전해주세요."),
    CHARGE_IN_PROGRESS(HttpStatus.CONFLICT, "CHARGE_IN_PROGRESS", "이 충전을 처리하고 있습니다. 잠시 후 다시 확인해주세요."),
    TOO_MANY_PENDING_CHARGES(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_CHARGES", "결제가 끝나지 않은 충전 주문이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "같은 요청 키를 다른 내용으로 사용할 수 없습니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", "이미 사용된 결제입니다."),
    CONCURRENT_REQUEST(HttpStatus.CONFLICT, "CONCURRENT_REQUEST", "요청이 겹쳐 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),
    ADMIN_SELF_REVOKE(HttpStatus.CONFLICT, "ADMIN_SELF_REVOKE", "본인의 운영자 권한은 해제할 수 없습니다."),
    NOT_AN_ADMIN(HttpStatus.CONFLICT, "NOT_AN_ADMIN", "운영자가 아닌 회원입니다."),
    BALANCE_REMAINING(HttpStatus.CONFLICT, "BALANCE_REMAINING", "남은 찰떡이 있는 회원입니다. 소멸에 동의하고 진행해야 합니다."),
    ADMIN_ACCOUNT_PROTECTED(HttpStatus.CONFLICT, "ADMIN_ACCOUNT_PROTECTED", "운영자 계정은 탈퇴 처리할 수 없습니다."),
    GUEST_IMAGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "GUEST_IMAGE_NOT_ALLOWED", "비회원은 사진을 첨부할 수 없습니다. 로그인해주세요."),
    PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_REQUIRED", "결제가 필요한 항목이 포함되어 있습니다."),
    INVALID_ORDER(HttpStatus.BAD_REQUEST, "INVALID_ORDER", "주문 항목이 올바르지 않습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_USED(HttpStatus.CONFLICT, "ORDER_ALREADY_USED", "이미 사용한 결제입니다."),
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "결제 수단이 아직 설정되지 않았습니다."),
    AD_NOT_COMPLETED(HttpStatus.FORBIDDEN, "AD_NOT_COMPLETED", "광고 시청을 완료해야 해제할 수 있습니다."),
    MESSAGE_NOT_READ(HttpStatus.CONFLICT, "MESSAGE_NOT_READ", "읽은 편지만 바구니로 옮길 수 있습니다."),
    MESSAGE_LOCKED(HttpStatus.CONFLICT, "MESSAGE_LOCKED", "아직 열어볼 수 없는 편지입니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "업로드 자산을 찾을 수 없습니다."),
    INVALID_MEDIA(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "업로드할 이미지 정보가 올바르지 않습니다."),
    MEDIA_UPLOAD_NOT_FOUND(HttpStatus.CONFLICT, "MEDIA_UPLOAD_NOT_FOUND", "Object Storage에서 업로드 파일을 확인할 수 없습니다."),
    STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Object Storage 설정이 필요합니다."),
    STORAGE_API_ERROR(HttpStatus.BAD_GATEWAY, "STORAGE_API_ERROR", "Object Storage와 통신하지 못했습니다."),
    ADVERTISEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ADVERTISEMENT_NOT_FOUND", "광고를 찾을 수 없습니다."),
    INVALID_ADVERTISEMENT(HttpStatus.BAD_REQUEST, "INVALID_ADVERTISEMENT", "광고 정보가 올바르지 않습니다."),
    INVALID_ANALYTICS_EVENT(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_EVENT", "분석 이벤트 정보가 올바르지 않습니다."),
    TOO_MANY_MESSAGES(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_MESSAGES", "응원을 너무 많이 보냈어요. 잠시 후 다시 시도해주세요."),
    DESK_BUSY(HttpStatus.TOO_MANY_REQUESTS, "DESK_BUSY", "이 책상에 응원이 한꺼번에 몰리고 있어요. 잠시 후 다시 시도해주세요."),
    TOO_MANY_EVENTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_EVENTS", "이벤트 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    MINIGAME_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "MINIGAME_ROUND_NOT_FOUND", "없거나 만료된 게임입니다. 새로 시작해주세요."),
    MINIGAME_ROUND_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "MINIGAME_ROUND_ALREADY_SUBMITTED", "이미 제출한 게임입니다."),
    INVALID_MINIGAME_ANSWERS(HttpStatus.BAD_REQUEST, "INVALID_MINIGAME_ANSWERS", "제출한 답이 올바르지 않습니다."),
    INVALID_MINIGAME_RESULT(HttpStatus.BAD_REQUEST, "INVALID_MINIGAME_RESULT", "게임 결과가 올바르지 않습니다."),
    INVALID_D1_COURSE_RESPONSE(HttpStatus.BAD_REQUEST, "INVALID_D1_COURSE_RESPONSE", "코스 응답 형식이 올바르지 않습니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 한글·영문·숫자·밑줄·공백으로 2~12자여야 합니다."),
    NICKNAME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "NICKNAME_NOT_ALLOWED", "사용할 수 없는 닉네임입니다. 다른 닉네임을 입력해주세요."),
    PUBLIC_FEED_DISABLED(HttpStatus.FORBIDDEN, "PUBLIC_FEED_DISABLED", "공개 응원 보기가 비활성화되어 있습니다."),
    GUEST_NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "GUEST_NICKNAME_REQUIRED", "비회원은 닉네임을 입력해야 합니다."),
    INVALID_MESSAGE_KIND(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_KIND", "지원하지 않는 응원 종류입니다."),
    INVALID_CARD_PAYLOAD(HttpStatus.BAD_REQUEST, "INVALID_CARD_PAYLOAD", "카드 데이터 형식이 올바르지 않습니다."),
    INVALID_DESK_OBJECT(HttpStatus.BAD_REQUEST, "INVALID_DESK_OBJECT", "책상 오브젝트 정보가 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."),
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
