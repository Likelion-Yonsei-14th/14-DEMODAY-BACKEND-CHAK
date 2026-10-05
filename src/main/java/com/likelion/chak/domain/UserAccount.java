package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String kakaoId;

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

    private UserAccount(String kakaoId, String displayName, String email, String profileImageUrl) {
        this.kakaoId = kakaoId;
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    public static UserAccount createKakao(
            String kakaoId,
            String displayName,
            String email,
            String profileImageUrl) {
        return new UserAccount(kakaoId, displayName, email, profileImageUrl);
    }

    public void updateKakaoProfile(String displayName, String email, String profileImageUrl) {
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
