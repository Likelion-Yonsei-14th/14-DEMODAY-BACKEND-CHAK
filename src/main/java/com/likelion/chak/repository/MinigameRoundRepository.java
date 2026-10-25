package com.likelion.chak.repository;

import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MinigameRoundRepository extends JpaRepository<MinigameRound, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MinigameRound r where r.publicId = :publicId")
    Optional<MinigameRound> findForUpdateByPublicId(@Param("publicId") String publicId);

    interface UserBest {
        Long getUserId();

        Integer getBest();
    }

    /** 기간 안에 제출한 회원별 최고 점수, 높은 순. 탈퇴한 회원은 뺀다. */
    @Query("""
            select r.user.id as userId, max(r.score) as best
            from MinigameRound r
            where r.game = :game and r.score is not null and r.user is not null
              and r.user.deletedAt is null and r.submittedAt >= :since
            group by r.user.id
            order by max(r.score) desc, min(r.submittedAt) asc, r.user.id asc""")
    List<UserBest> topBests(@Param("game") MinigameType game, @Param("since") Instant since, Pageable pageable);

    @Query("""
            select max(r.score) from MinigameRound r
            where r.game = :game and r.user.id = :userId and r.score is not null and r.submittedAt >= :since""")
    Integer bestOf(@Param("game") MinigameType game, @Param("userId") Long userId, @Param("since") Instant since);

    /** 나보다 높은 최고 점수를 가진 회원 수. 순위 = 이 값 + 1. */
    @Query("""
            select count(distinct r.user.id) from MinigameRound r
            where r.game = :game and r.score > :score and r.user is not null
              and r.user.deletedAt is null and r.submittedAt >= :since""")
    long countUsersAbove(@Param("game") MinigameType game, @Param("score") int score, @Param("since") Instant since);

    interface ScoreCount {
        Integer getScore();

        Long getCount();
    }

    /** 점수별 제출 수(비회원 포함). */
    @Query("""
            select r.score as score, count(r) as count from MinigameRound r
            where r.game = :game and r.score is not null and r.submittedAt >= :since
            group by r.score""")
    List<ScoreCount> distribution(@Param("game") MinigameType game, @Param("since") Instant since);

    /** 기간 안에 제출한 판의 (제출 시각, 점수, 회원 번호 또는 null). 어드민 통계가 자바에서 날짜별로 묶는다. */
    @Query("""
            select r.submittedAt, r.score, u.id
            from MinigameRound r left join r.user u
            where r.game = :game and r.score is not null and r.submittedAt >= :since""")
    List<Object[]> submissionsSince(@Param("game") MinigameType game, @Param("since") Instant since);

    long countByGameAndCreatedAtGreaterThanEqual(MinigameType game, Instant since);

    @Query("select count(distinct r.user.id) from MinigameRound r where r.game = :game and r.score is not null and r.user is not null")
    long countRankedMembers(@Param("game") MinigameType game);

    @Query("select max(r.score) from MinigameRound r where r.game = :game and r.score is not null and r.user is not null")
    Integer bestMemberScore(@Param("game") MinigameType game);
}
