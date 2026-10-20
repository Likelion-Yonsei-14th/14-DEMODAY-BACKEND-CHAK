package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 관리자가 한 중요한 작업(회원 탈퇴 처리 등)의 기록. */
@Getter
@Entity
@Table(name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private UserAccount admin;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 40)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public AdminAuditLog(UserAccount admin, String action, String targetType, Long targetId, String detail) {
        this.admin = admin;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
