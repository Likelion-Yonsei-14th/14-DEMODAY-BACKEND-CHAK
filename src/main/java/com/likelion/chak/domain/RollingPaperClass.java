package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/** 연말 롤링페이퍼(반 또는 개인). 초대 링크는 추측하기 어려운 {@code code}로 연다. */
@Getter
@Entity
@Table(name = "rolling_paper_classes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RollingPaperClass {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RollingPaperRevealMode revealMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RollingPaperKind kind;

    private Instant endedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private RollingPaperClass(String code, String name, RollingPaperRevealMode revealMode, RollingPaperKind kind) {
        this.code = code;
        this.name = name;
        this.revealMode = revealMode;
        this.kind = kind;
    }

    public static RollingPaperClass create(String name, RollingPaperRevealMode revealMode) {
        return create(name, revealMode, RollingPaperKind.CLASS);
    }

    public static RollingPaperClass create(String name, RollingPaperRevealMode revealMode, RollingPaperKind kind) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RollingPaperClass(code, name, revealMode, kind == null ? RollingPaperKind.CLASS : kind);
    }

    public boolean isPersonal() {
        return kind == RollingPaperKind.PERSONAL;
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeRevealMode(RollingPaperRevealMode revealMode) {
        this.revealMode = revealMode;
    }

    public void end(Instant now) {
        if (endedAt == null) {
            this.endedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    /** 주인이 자기 종이를 볼 수 있는가. */
    public boolean isOwnPaperOpen() {
        return revealMode == RollingPaperRevealMode.INSTANT || isEnded();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
