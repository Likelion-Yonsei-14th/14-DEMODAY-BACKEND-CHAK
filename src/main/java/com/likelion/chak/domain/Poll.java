package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 수능 여론 질문 하나. 읽고 쓰는 일은 {@code PollService}가 SQL로 하고, 이 클래스는 표 모양을 정한다. */
@Getter
@Entity
@Table(name = "polls", indexes = @Index(name = "idx_polls_opens", columnList = "opens_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_polls_slug", columnNames = "slug"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false, length = 30)
    private String tag;

    @Column(nullable = false, length = 120)
    private String question;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
