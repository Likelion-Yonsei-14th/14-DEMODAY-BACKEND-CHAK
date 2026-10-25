package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** D+1 코스 짜기 응답 한 건. 누가 답했는지는 저장하지 않는다. */
@Getter
@Entity
@Table(name = "d1_course_responses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class D1CourseResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String responseId;

    @Column(nullable = false, length = 20)
    private String startRegion;

    @Column(length = 20)
    private String startDistrict;

    @Column(nullable = false, length = 20)
    private String area;

    @Column(nullable = false, length = 20)
    private String activity1;

    @Column(nullable = false, length = 20)
    private String activity2;

    @Column(nullable = false, length = 20)
    private String activity3;

    @Column(nullable = false, length = 20)
    private String companion;

    @Column(nullable = false, length = 20)
    private String budget;

    @Column(nullable = false)
    private boolean shared;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public D1CourseResponse(String responseId, String startRegion, String startDistrict, String area,
                            String activity1, String activity2, String activity3,
                            String companion, String budget, boolean shared) {
        this.responseId = responseId;
        this.startRegion = startRegion;
        this.startDistrict = startDistrict;
        this.area = area;
        this.activity1 = activity1;
        this.activity2 = activity2;
        this.activity3 = activity3;
        this.companion = companion;
        this.budget = budget;
        this.shared = shared;
    }

    /** 같은 응답을 다시 받았을 때: 공유했음 표시만 올린다. 답 내용은 처음 받은 그대로 둔다. */
    public void markShared() {
        this.shared = true;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
