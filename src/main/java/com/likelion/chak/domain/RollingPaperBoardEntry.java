package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 롤링페이퍼 교실 칠판에 반 친구가 적은 한 줄. */
@Getter
@Entity
@Table(name = "rolling_paper_board_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RollingPaperBoardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private RollingPaperClass paperClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_member_id", nullable = false)
    private RollingPaperMember author;

    @Column(name = "entry_text", nullable = false, length = 30)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private RollingPaperBoardEntry(RollingPaperMember author, String text) {
        this.paperClass = author.getPaperClass();
        this.author = author;
        this.text = text;
    }

    public static RollingPaperBoardEntry create(RollingPaperMember author, String text) {
        return new RollingPaperBoardEntry(author, text);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
