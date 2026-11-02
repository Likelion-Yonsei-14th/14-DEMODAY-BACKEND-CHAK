package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 수험생 커리어 시뮬레이터 한 판. 시작할 때 만들고, 수능과 진학까지 끝나면 결과를 채운다. 누가 했는지는 저장하지 않는다. */
@Getter
@Entity
@Table(name = "exam_career_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamCareerRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String runId;

    @Column(nullable = false, length = 12)
    private String track;

    @Column(nullable = false)
    private boolean finished;

    /** 끝난 판의 재수 횟수(0이면 현역). */
    private Integer attempt;

    @Column(precision = 3, scale = 1)
    private BigDecimal finalAverage;

    /** 진학한 대학 단계 id. 진학하지 않았으면 "none". */
    @Column(length = 20)
    private String enrolled;

    @Column(length = 30)
    private String title;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ExamCareerRun(String runId, String track) {
        this.runId = runId;
        this.track = track;
    }

    /** 끝난 결과를 한 번만 받는다. 이미 끝난 판이면 false. */
    public boolean finish(int attempt, BigDecimal finalAverage, String enrolled, String title) {
        if (finished) {
            return false;
        }
        this.finished = true;
        this.attempt = attempt;
        this.finalAverage = finalAverage;
        this.enrolled = enrolled;
        this.title = title;
        return true;
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
