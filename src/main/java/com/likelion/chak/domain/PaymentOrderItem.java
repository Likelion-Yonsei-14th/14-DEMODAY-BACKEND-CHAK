package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PaymentOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", nullable = false, length = 40)
    private ProductCode code;

    @Column(name = "item_ref", length = 100)
    private String ref;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int amount;

    private PaymentOrderItem(PaymentOrder order, ProductCode code, String ref, int quantity) {
        this.order = order;
        this.code = code;
        this.ref = ref;
        this.quantity = quantity;
        this.unitPrice = code.unitPrice();
        this.amount = code.unitPrice() * quantity;
    }

    public static PaymentOrderItem create(PaymentOrder order, ProductCode code, String ref, int quantity) {
        return new PaymentOrderItem(order, code, ref, quantity);
    }
}
