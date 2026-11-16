package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 한 사람의 한 표. voter_key는 'u:회원번호' 또는 'g:손님번호'이고, IP는 저장하지 않는다. */
@Getter
@Entity
@Table(name = "poll_votes",
        indexes = @Index(name = "idx_poll_votes_poll_option", columnList = "poll_id, option_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_poll_votes_voter", columnNames = {"poll_id", "voter_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @Column(name = "voter_key", nullable = false, length = 40)
    private String voterKey;

    @Column(length = 20)
    private String region;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
