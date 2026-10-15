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
    @Column(nullable = false, updatable = false, length = 100)
    private String providerUserId;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(length = 320)
    private String email;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean admin;

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

    public void updateProfile(String displayName, String email, String profileImageUrl) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
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
