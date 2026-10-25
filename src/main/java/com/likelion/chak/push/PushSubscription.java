package com.likelion.chak.push;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 브라우저 한 곳의 답장 알림 구독. */
@Getter
@Entity
@Table(name = "push_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long guestId;

    @Column(nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(nullable = false, length = 100)
    private String p256dh;

    @Column(nullable = false, length = 40)
    private String auth;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastSuccessAt;

    @Column(nullable = false)
    private int failureCount;

    public PushSubscription(Long userId, Long guestId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.guestId = guestId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    /** 같은 브라우저가 다시 구독했다: 키를 새로 받고 구독한 사람을 지금 사람으로 바꾼다. */
    public void renew(Long userId, Long guestId, String p256dh, String auth) {
        this.userId = userId;
        this.guestId = guestId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.failureCount = 0;
    }

    public void markDelivered(Instant at) {
        this.lastSuccessAt = at;
        this.failureCount = 0;
    }

    public void markFailed() {
        this.failureCount++;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
