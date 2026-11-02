package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 로그인한 사용자가 즐겨찾기한 친구 책상. */
@Getter
@Entity
@Table(name = "favorite_desks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteDesk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desk_id", nullable = false)
    private PersonalDesk desk;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private FavoriteDesk(UserAccount user, PersonalDesk desk) {
        this.user = user;
        this.desk = desk;
    }

    public static FavoriteDesk create(UserAccount user, PersonalDesk desk) {
        return new FavoriteDesk(user, desk);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
