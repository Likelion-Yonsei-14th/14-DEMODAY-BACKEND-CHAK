package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영자 초대. 이메일은 소문자로 저장하며, 구글이 확인한 같은 이메일로 로그인하면 적용된다. */
@Getter
@Entity
@Table(name = "admin_invites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 320)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserAccount createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant consumedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumed_user_id")
    private UserAccount consumedUser;

    public AdminInvite(String email, UserAccount createdBy) {
        this.email = email;
        this.createdBy = createdBy;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void consume(UserAccount user, Instant at) {
        this.consumedUser = user;
        this.consumedAt = at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
