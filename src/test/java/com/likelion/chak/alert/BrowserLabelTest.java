package com.likelion.chak.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserLabelTest {

    @Test
    void detectsInAppBrowsersAndPlatforms() {
        assertThat(BrowserLabel.of("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 KAKAOTALK 10.0"))
                .isEqualTo("iOS · 카카오톡 인앱");
        assertThat(BrowserLabel.of("Mozilla/5.0 (Linux; Android 14) Chrome/120.0 Mobile Safari/537.36"))
                .isEqualTo("Android · Chrome");
        assertThat(BrowserLabel.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15"))
                .isEqualTo("macOS · Safari");
        assertThat(BrowserLabel.of(null)).isEqualTo("알 수 없음");
    }
}
