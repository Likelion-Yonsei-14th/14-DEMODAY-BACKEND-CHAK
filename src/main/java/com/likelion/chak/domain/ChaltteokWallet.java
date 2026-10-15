package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 회원의 찰떡 잔액. 잔액을 바꿀 때는 반드시 행 잠금(findForUpdate)을 잡고 한다. */
@Getter
@Entity
@Table(name = "chaltteok_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChaltteokWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(nullable = false)
    private long balance;

    @Version
    private long version;

    /** 토스 결제위젯에 쓰는 구매자 식별자. 추측할 수 없는 무작위 값이어야 하므로 계정 정보와 무관하게 만든다. */
    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String customerKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private ChaltteokWallet(UserAccount user) {
        this.user = user;
        this.balance = 0;
        this.customerKey = UUID.randomUUID().toString();
    }

    public static ChaltteokWallet create(UserAccount user) {
        return new ChaltteokWallet(user);
    }

    public void credit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("credit must be positive");
        }
        this.balance += amount;
    }

    public void debit(long amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("invalid debit");
        }
        this.balance -= amount;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
