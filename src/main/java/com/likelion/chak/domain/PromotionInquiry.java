package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 광고 상담 신청 한 건. 담당자 연락처가 들어 있어 어드민에서만 보고, 오래된 건은 정기적으로 삭제한다. */
@Getter
@Entity
@Table(name = "promotion_inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionInquiry {

    public enum Interest { PACKAGE, BANNER, OTHER }

    public enum Status { NEW, CONTACTED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 50)
    private String contactName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 50)
    private String category;

    /** 상담 신청서에서 고른 관심 구성. 고르지 않아도 되는 항목이다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Interest interest;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant handledAt;

    public PromotionInquiry(String company, String contactName, String email, String phone,
                            String category, Interest interest, String message) {
        this.company = company;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.category = category;
        this.interest = interest;
        this.message = message;
    }

    public void changeStatus(Status next, Instant at) {
        this.status = next;
        this.handledAt = next == Status.NEW ? null : at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
