package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "chaltteok_charges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChaltteokCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String orderId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status;

    @Column(length = 200)
    private String paymentKey;

    private Long resultBalance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    /** 승인 요청을 선점한 시각. 한 번이라도 승인을 시도한 주문은 만료되지 않는다. */
    private Instant claimedAt;

    private ChaltteokCharge(UserAccount user, String orderId, long amount) {
        this.user = user;
        this.orderId = orderId;
        this.amount = amount;
        this.status = ChargeStatus.READY;
    }

    public static ChaltteokCharge create(UserAccount user, String orderId, long amount) {
        return new ChaltteokCharge(user, orderId, amount);
    }

    public boolean isExpired(Instant now, java.time.Duration ttl) {
        return status == ChargeStatus.READY && claimedAt == null && createdAt.plus(ttl).isBefore(now);
    }

    public boolean isDone() {
        return status == ChargeStatus.DONE;
    }

    public void complete(String paymentKey, long resultBalance, Instant at) {
        this.status = ChargeStatus.DONE;
        this.paymentKey = paymentKey;
        this.resultBalance = resultBalance;
        this.confirmedAt = at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
