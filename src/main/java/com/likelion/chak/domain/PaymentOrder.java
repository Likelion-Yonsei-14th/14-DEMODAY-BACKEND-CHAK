package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "payment_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_user_id")
    private UserAccount payerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_guest_id")
    private GuestIdentity payerGuest;

    @Column(nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(length = 100)
    private String providerRef;

    private Instant consumedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant paidAt;

    private PaymentOrder(UserAccount user, GuestIdentity guest, int totalAmount, String provider) {
        this.payerUser = user;
        this.payerGuest = guest;
        this.totalAmount = totalAmount;
        this.provider = provider;
        this.status = OrderStatus.CREATED;
    }

    public static PaymentOrder create(UserAccount user, GuestIdentity guest, int totalAmount, String provider) {
        return new PaymentOrder(user, guest, totalAmount, provider);
    }

    public boolean belongsTo(UserAccount user, GuestIdentity guest) {
        if (user != null && payerUser != null) {
            return payerUser.getId().equals(user.getId());
        }
        if (guest != null && payerGuest != null) {
            return payerGuest.getId().equals(guest.getId());
        }
        return false;
    }

    public void markPaid(String providerRef, Instant at) {
        this.status = OrderStatus.PAID;
        this.providerRef = providerRef;
        this.paidAt = at;
    }

    public void markConsumed(Instant at) {
        this.consumedAt = at;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
