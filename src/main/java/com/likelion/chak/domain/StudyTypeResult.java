package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** "나의 수험생 유형은?" 결과 한 건. 회원은 한 줄만 남고 다시 하면 바뀐다. */
@Getter
@Entity
@Table(name = "study_type_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyTypeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public StudyTypeResult(String code, UserAccount user) {
        this.code = code;
        this.user = user;
    }

    public void changeCode(String code) {
        this.code = code;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
