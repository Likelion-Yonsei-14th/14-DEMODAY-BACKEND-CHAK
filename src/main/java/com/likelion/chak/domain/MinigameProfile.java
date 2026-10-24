package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 미니게임 랭킹에 보이는 회원 닉네임. */
@Getter
@Entity
@Table(name = "minigame_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinigameProfile {

    @Id
    private Long userId;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Column(nullable = false)
    private Instant updatedAt;

    public MinigameProfile(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }

    public void rename(String nickname) {
        this.nickname = nickname;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
