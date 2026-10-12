package com.likelion.chak.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyValidatorTest {

    @Test
    void rejectsLocalDefaultSecret() {
        assertThatThrownBy(() -> new ProductionSafetyValidator(
                "chak-local-development-secret-key-must-be-changed-2026").validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new ProductionSafetyValidator("short").validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongSecret() {
        assertThatCode(() -> new ProductionSafetyValidator(
                "a-production-secret-with-at-least-32-bytes!!").validate())
                .doesNotThrowAnyException();
    }
}
