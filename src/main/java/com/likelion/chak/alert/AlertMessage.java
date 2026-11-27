package com.likelion.chak.alert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 운영자에게 보낼 알림 한 건. 내용은 모두 {@link AlertSanitizer}를 거쳐 개인정보·토큰이 제거된 값이어야 한다.
 *
 * @param source      발생 위치("서버" / "프론트")
 * @param title       한 줄 제목
 * @param description 본문(짧게)
 * @param fields      이름-값 상세
 * @param fingerprint 같은 오류를 묶는 키
 * @param severity    ERROR(서버·결제 문제) 또는 WARN(클라이언트 오류)
 * @param userReported 로그인 없이 누구나 보낼 수 있는 내용(브라우저 오류 제보)인지. 알림 한도를 따로 쓰고,
 *                     디스코드에서는 링크·서식이 살아나지 않게 코드 블록으로 감싸 "확인되지 않은 제보"로 표시한다.
 */
public record AlertMessage(
        String source,
        String title,
        String description,
        Map<String, String> fields,
        String fingerprint,
        Severity severity,
        boolean userReported) {

    public AlertMessage(String source, String title, String description, Map<String, String> fields,
                        String fingerprint, Severity severity) {
        this(source, title, description, fields, fingerprint, severity, false);
    }

    public enum Severity { WARN, ERROR }

    public static Builder builder(String source, String title, String fingerprint, Severity severity) {
        return new Builder(source, title, fingerprint, severity);
    }

    public AlertMessage withDescription(String newDescription) {
        return new AlertMessage(source, title, newDescription, fields, fingerprint, severity, userReported);
    }

    public static final class Builder {
        private final String source;
        private final String title;
        private final String fingerprint;
        private final Severity severity;
        private String description = "";
        private boolean userReported;
        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder(String source, String title, String fingerprint, Severity severity) {
            this.source = source;
            this.title = title;
            this.fingerprint = fingerprint;
            this.severity = severity;
        }

        public Builder description(String value) {
            this.description = value == null ? "" : value;
            return this;
        }

        public Builder field(String name, String value) {
            if (value != null && !value.isBlank()) {
                fields.put(name, value);
            }
            return this;
        }

        /** 누구나 보낼 수 있는(검증되지 않은) 내용이라고 표시한다. */
        public Builder userReported() {
            this.userReported = true;
            return this;
        }

        public AlertMessage build() {
            return new AlertMessage(source, title, description, fields, fingerprint, severity, userReported);
        }
    }
}
