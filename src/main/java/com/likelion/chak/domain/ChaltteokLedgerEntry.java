package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "chaltteok_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChaltteokLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerType type;

    /** 충전은 양수, 구매는 음수. */
    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 100, updatable = false)
    private String ref;

    @Column(length = 64, updatable = false)
    private String idempotencyKey;

    @Column(length = 64, updatable = false)
    private String requestHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private ChaltteokLedgerEntry(UserAccount user, LedgerType type, long amount, long balanceAfter, String ref,
                                 String idempotencyKey, String requestHash) {
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.ref = ref;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public static ChaltteokLedgerEntry charge(UserAccount user, long amount, long balanceAfter, String orderId) {
        return new ChaltteokLedgerEntry(user, LedgerType.CHARGE, amount, balanceAfter, orderId, null, null);
    }

    public static ChaltteokLedgerEntry purchase(
            UserAccount user, long spent, long balanceAfter, Long orderId, String idempotencyKey, String requestHash) {
        return new ChaltteokLedgerEntry(
                user, LedgerType.PURCHASE, -spent, balanceAfter, "order:" + orderId, idempotencyKey, requestHash);
    }

    /** 구매 장부의 ref("order:{id}")에서 결제 주문 id를 꺼낸다. */
    public Long orderIdFromRef() {
        return Long.valueOf(ref.substring("order:".length()));
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
