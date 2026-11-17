package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** 광고 제휴를 위해 연락 중인 매장 한 곳. 팀원이 어드민에서 함께 기록하고 상태를 바꾼다. */
@Getter
@Entity
@Table(name = "store_outreach")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOutreach {

    /** 연락 수단. 소상공인은 인스타 DM·전화, 본사·대기업은 이메일·제휴 폼·링크드인을 주로 쓴다. */
    public enum Channel { INSTAGRAM, PHONE, EMAIL, NAVER_TALK, WEBSITE, LINKEDIN, KAKAO, VISIT, OTHER }

    /** 연락 전 → 연락함 → 답장 옴 → 협의 중 → 합의 → 입금 완료. 거절·무응답은 끝난 상태다. */
    public enum Status { TODO, CONTACTED, REPLIED, NEGOTIATING, AGREED, PAID, REJECTED, NO_RESPONSE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String storeName;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(length = 300)
    private String contact;

    @Column(length = 30)
    private String assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.TODO;

    @Column(length = 2000)
    private String note;

    private LocalDate lastContactedOn;

    @Column(length = 50)
    private String updatedByName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public StoreOutreach(String storeName, Channel channel) {
        this.storeName = storeName;
        this.channel = channel;
    }

    public void update(String storeName, String category, String region, Channel channel, String contact,
                       String assignee, Status status, String note, LocalDate lastContactedOn, String updatedByName) {
        this.storeName = storeName;
        this.category = category;
        this.region = region;
        this.channel = channel;
        this.contact = contact;
        this.assignee = assignee;
        this.status = status == null ? Status.TODO : status;
        this.note = note;
        this.lastContactedOn = lastContactedOn;
        this.updatedByName = updatedByName;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
