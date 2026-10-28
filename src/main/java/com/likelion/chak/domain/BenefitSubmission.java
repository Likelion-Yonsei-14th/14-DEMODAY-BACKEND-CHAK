package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 제휴 업체가 보낸 혜택 시안 신청 한 건. 담당자 연락처가 들어 있어 어드민에서만 보고, 오래된 건은 정기적으로 삭제한다. */
@Getter
@Entity
@Table(name = "benefit_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 50)
    private String contactName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    /** 광고 시안 메인 문구로 쓰는 한 줄 요약(20자 이내). */
    @Column(nullable = false, length = 40)
    private String headline;

    /** 검증을 마친 답변 전체(JSON 문자열). */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String answers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionInquiry.Status status = PromotionInquiry.Status.NEW;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant handledAt;

    public BenefitSubmission(String brand, String category, String contactName, String email, String phone,
                             String headline, String answers) {
        this.brand = brand;
        this.category = category;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.headline = headline;
        this.answers = answers;
    }

    public void changeStatus(PromotionInquiry.Status next, Instant at) {
        this.status = next;
        this.handledAt = next == PromotionInquiry.Status.NEW ? null : at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
