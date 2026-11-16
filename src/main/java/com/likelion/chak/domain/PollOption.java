package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 질문 하나의 선택지. */
@Getter
@Entity
@Table(name = "poll_options", indexes = @Index(name = "idx_poll_options_poll", columnList = "poll_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
