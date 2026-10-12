package com.likelion.chak.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** prod 프로필에서 로컬 기본 시크릿이나 짧은 시크릿으로 기동하는 것을 막는다. */
@Component
@Profile("prod")
public class ProductionSafetyValidator {

    private static final String LOCAL_SECRET_PREFIX = "chak-local-development";
    private static final int MIN_SECRET_BYTES = 32;

    private final String jwtSecret;

    public ProductionSafetyValidator(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret.startsWith(LOCAL_SECRET_PREFIX)) {
            throw new IllegalStateException("JWT_SECRET must be set to a production value in the prod profile");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
    }
}
