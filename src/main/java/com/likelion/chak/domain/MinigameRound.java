package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 미니게임 한 판. 정답은 서버가 정하고, 한 번만 제출할 수 있다. */
@Getter
@Entity
@Table(name = "minigame_rounds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinigameRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinigameType game;

    /** 문항별 정답을 이어 붙인 숫자 문자열(예: "3142513425"). */
    @Column(nullable = false, length = 40)
    private String answerKey;

    /** 제출한 로그인 회원. 비회원이 제출하면 비어 있고 랭킹에 오르지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    private Integer score;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant submittedAt;

    public MinigameRound(MinigameType game, String answerKey) {
        this.publicId = UUID.randomUUID().toString();
        this.game = game;
        this.answerKey = answerKey;
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public void submit(UserAccount user, int score, Instant at) {
        this.user = user;
        this.score = score;
        this.submittedAt = at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
