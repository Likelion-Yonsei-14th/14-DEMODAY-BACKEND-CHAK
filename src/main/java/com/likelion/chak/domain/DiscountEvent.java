package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 혜택함 제휴 광고의 노출·클릭 한 건. 회원이면 userId, 손님이면 guestId가 있다. */
@Getter
@Entity
@Table(name = "discount_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountEvent {

    public enum Type { IMPRESSION, CLICK }

    /** 어디서 봤는지: 혜택 목록 카드, 지도 매장 카드. */
    public enum Placement { LIST, MAP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discount_id", nullable = false)
    private Long discountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private Type eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Placement placement;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_id")
    private Long guestId;

    @Column(length = 20)
    private String region;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DiscountEvent(Long discountId, Type eventType, Placement placement, Long userId, Long guestId, String region) {
        this.discountId = discountId;
        this.eventType = eventType;
        this.placement = placement;
        this.userId = userId;
        this.guestId = guestId;
        this.region = region;
        this.createdAt = Instant.now();
    }
}
