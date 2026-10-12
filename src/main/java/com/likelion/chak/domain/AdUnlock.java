package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 광고를 끝까지 보고 해제한 편지지·꾸미기 소품. 해제한 사람(회원 또는 비회원)에게만 적용된다. */
@Getter
@Entity
@Table(name = "ad_unlocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private GuestIdentity guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnlockCategory category;

    @Column(nullable = false, length = 100)
    private String assetId;

    @Column(length = 100)
    private String adSessionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private AdUnlock(UserAccount user, GuestIdentity guest, UnlockCategory category, String assetId, String adSessionId) {
        this.user = user;
        this.guest = guest;
        this.category = category;
        this.assetId = assetId;
        this.adSessionId = adSessionId;
    }

    public static AdUnlock create(
            UserAccount user, GuestIdentity guest, UnlockCategory category, String assetId, String adSessionId) {
        return new AdUnlock(user, guest, category, assetId, adSessionId);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
