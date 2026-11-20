package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 롤링페이퍼 반의 한 사람이자 그 사람의 사물함(롤링페이퍼 한 장). 회원 또는 비회원 중 하나다. */
@Getter
@Entity
@Table(name = "rolling_paper_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RollingPaperMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private RollingPaperClass paperClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private GuestIdentity guest;

    @Column(nullable = false, length = 12)
    private String displayName;

    @Column(nullable = false, length = 48)
    private String paperDesign;

    @Column(nullable = false)
    private boolean creator;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private RollingPaperMember(RollingPaperClass paperClass, UserAccount user, GuestIdentity guest,
                               String displayName, String paperDesign, boolean creator) {
        this.paperClass = paperClass;
        this.user = user;
        this.guest = guest;
        this.displayName = displayName;
        this.paperDesign = paperDesign;
        this.creator = creator;
    }

    public static RollingPaperMember create(RollingPaperClass paperClass, UserAccount user, GuestIdentity guest,
                                            String displayName, String paperDesign, boolean creator) {
        if ((user == null) == (guest == null)) {
            throw new IllegalArgumentException("회원 또는 비회원 중 하나만 지정해야 합니다.");
        }
        return new RollingPaperMember(paperClass, user, guest, displayName, paperDesign, creator);
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changeDesign(String paperDesign) {
        this.paperDesign = paperDesign;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
