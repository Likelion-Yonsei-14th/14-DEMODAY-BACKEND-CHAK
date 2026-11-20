package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 한 사람이 친구의 롤링페이퍼에 남긴 한마디. 같은 종이에는 하나만 남기고, 다시 쓰면 고치거나 옮긴다. */
@Getter
@Entity
@Table(name = "rolling_paper_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RollingPaperNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private RollingPaperClass paperClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_member_id", nullable = false)
    private RollingPaperMember paper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_member_id", nullable = false)
    private RollingPaperMember author;

    @Column(name = "note_text", nullable = false, length = 60)
    private String text;

    @Column(nullable = false, length = 16)
    private String pen;

    @Column(nullable = false, length = 16)
    private String stamp;

    @Column(name = "pos_x", nullable = false, precision = 5, scale = 2)
    private BigDecimal x;

    @Column(name = "pos_y", nullable = false, precision = 5, scale = 2)
    private BigDecimal y;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private RollingPaperNote(RollingPaperMember paper, RollingPaperMember author) {
        this.paperClass = paper.getPaperClass();
        this.paper = paper;
        this.author = author;
    }

    public static RollingPaperNote create(RollingPaperMember paper, RollingPaperMember author,
                                          String text, String pen, String stamp, BigDecimal x, BigDecimal y) {
        RollingPaperNote note = new RollingPaperNote(paper, author);
        note.write(text, pen, stamp, x, y);
        return note;
    }

    public void write(String text, String pen, String stamp, BigDecimal x, BigDecimal y) {
        this.text = text;
        this.pen = pen;
        this.stamp = stamp;
        this.x = x;
        this.y = y;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
