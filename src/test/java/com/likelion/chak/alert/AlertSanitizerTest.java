package com.likelion.chak.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSanitizerTest {

    @Test
    void masksEmailsTokensAndLongSecrets() {
        String cleaned = AlertSanitizer.clean(
                "user a.b+c@yonsei.ac.kr failed with Bearer abc.def-123 and eyJhbGciOiJIUzI1.eyJzdWIiOiIxIn0.SflKxwRJSMeKKF2QT4 key=" + "k".repeat(40), 500);
        assertThat(cleaned).doesNotContain("yonsei", "abc.def-123", "eyJhbGci", "kkkkkkkk");
        assertThat(cleaned).contains("[이메일]", "[토큰]", "[값]");
    }

    @Test
    void truncatesAndStripsControlCharacters() {
        assertThat(AlertSanitizer.clean("abc ".repeat(20), 10)).hasSize(10).endsWith("…");
        assertThat(AlertSanitizer.clean("line1\u0000\u0007line2", 50)).isEqualTo("line1  line2");
        assertThat(AlertSanitizer.clean(null, 10)).isEmpty();
    }

    @Test
    void pathDropsQueryFragmentAndHost() {
        assertThat(AlertSanitizer.path("/desk/abc?token=secret#x", 100)).isEqualTo("/desk/abc");
        assertThat(AlertSanitizer.path("https://www.chak.live/auth/google/callback?code=zzz", 100))
                .isEqualTo("/auth/google/callback");
    }
}
