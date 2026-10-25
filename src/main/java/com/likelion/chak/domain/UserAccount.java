package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuthProvider provider;

    /** 제공자가 발급한 불변 식별자(카카오 회원번호, 구글 sub). (provider, providerUserId)가 유일하다. */
    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(length = 320)
    private String email;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean admin;

    /** 회원이 이름을 직접 고쳤는지. 그러면 다음 로그인에서 카카오·구글 이름으로 덮어쓰지 않는다. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean profileCustomized;

    private Instant lastLoginAt;

    /** 탈퇴 처리한 시각. 값이 있으면 개인정보를 지운 익명 계정이다(행은 장부·응원의 FK 때문에 남긴다). */
    private Instant deletedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UserAccount(
            AuthProvider provider, String providerUserId, String displayName, String email, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    public static UserAccount create(
            AuthProvider provider,
            String providerUserId,
            String displayName,
            String email,
            String profileImageUrl) {
        return new UserAccount(provider, providerUserId, displayName, email, profileImageUrl);
    }

    public static UserAccount createKakao(
            String kakaoId,
            String displayName,
            String email,
            String profileImageUrl) {
        return create(AuthProvider.KAKAO, kakaoId, displayName, email, profileImageUrl);
    }

    public static UserAccount createGoogle(
            String googleId,
            String displayName,
            String email,
            String profileImageUrl) {
        return create(AuthProvider.GOOGLE, googleId, displayName, email, profileImageUrl);
    }

    /** 로그인할 때 카카오·구글에서 받은 정보를 반영한다. 직접 고친 이름은 그대로 둔다. */
    public void updateProfile(String displayName, String email, String profileImageUrl) {
        if (!profileCustomized) {
            this.displayName = displayName;
        }
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /** 회원이 직접 이름을 고쳤다. */
    public void editDisplayName(String displayName) {
        this.displayName = displayName;
        this.profileCustomized = true;
    }

    public static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /**
     * 개인정보를 지우고 익명 계정으로 바꾼다. 로그인 식별자도 바꿔서 같은 카카오·구글 계정으로 다시 가입하면
     * 새 계정이 만들어지고, 이 행은 응원·결제 장부의 연결만 유지한다.
     */
    public void anonymize(Instant at) {
        this.displayName = WITHDRAWN_NAME;
        this.email = null;
        this.profileImageUrl = null;
        this.admin = false;
        this.profileCustomized = false;
        this.providerUserId = "deleted:" + id;
        this.deletedAt = at;
    }

    public void updateAdmin(boolean admin) {
        this.admin = admin;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
