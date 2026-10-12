package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 책상 주인이 숨긴 작성자. 작성자의 편지는 주인의 받은 편지와 공개 목록에서 제외된다. */
@Getter
@Entity
@Table(name = "desk_blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeskBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desk_id", nullable = false)
    private PersonalDesk desk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private UserAccount authorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_guest_id")
    private GuestIdentity authorGuest;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private DeskBlock(PersonalDesk desk, UserAccount user, GuestIdentity guest, String displayName) {
        this.desk = desk;
        this.authorUser = user;
        this.authorGuest = guest;
        this.displayName = displayName;
    }

    public static DeskBlock create(PersonalDesk desk, UserAccount user, GuestIdentity guest, String displayName) {
        return new DeskBlock(desk, user, guest, displayName);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
