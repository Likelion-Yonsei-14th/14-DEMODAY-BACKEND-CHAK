package com.likelion.chak.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 프로필의 안전한 기본값이 실수로 풀리지 않게 지킨다. */
class ProductionDefaultsTest {

    private final Properties prod = load();

    private static Properties load() {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void refreshTokenStaysOutOfTheResponseBodyUnlessExplicitlyEnabled() {
        assertThat(prod.getProperty("chak.refresh-cookie.include-in-body")).isEqualTo("${REFRESH_TOKEN_IN_BODY:false}");
    }

    @Test
    void cookiesAreSecureSwaggerIsOffAndPaymentsAreOffByDefault() {
        assertThat(prod.getProperty("chak.refresh-cookie.secure")).isEqualTo("${REFRESH_COOKIE_SECURE:true}");
        assertThat(prod.getProperty("chak.guest-cookie.secure")).isEqualTo("${GUEST_COOKIE_SECURE:true}");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("${SWAGGER_ENABLED:false}");
        assertThat(prod.getProperty("chak.payment.provider")).isEqualTo("${PAYMENT_PROVIDER:none}");
        assertThat(prod.getProperty("chak.chaltteok.provider")).isEqualTo("${CHALTTEOK_PROVIDER:none}");
    }
}
