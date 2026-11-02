package com.likelion.chak.repository;

import com.likelion.chak.domain.ExamCareerRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExamCareerRunRepository extends JpaRepository<ExamCareerRun, Long> {

    Optional<ExamCareerRun> findByRunId(String runId);

    long countByFinishedTrue();

    interface KeyCount {
        String getKey();

        Long getCount();
    }

    @Query("select r.track as key, count(r) as count from ExamCareerRun r group by r.track")
    List<KeyCount> countByTrack();

    @Query("select cast(r.attempt as string) as key, count(r) as count from ExamCareerRun r where r.finished = true group by r.attempt")
    List<KeyCount> countByAttempt();

    @Query("select r.enrolled as key, count(r) as count from ExamCareerRun r where r.finished = true group by r.enrolled")
    List<KeyCount> countByEnrolled();

    @Query("select r.title as key, count(r) as count from ExamCareerRun r where r.finished = true group by r.title")
    List<KeyCount> countByTitle();

    @Query("select avg(r.finalAverage) from ExamCareerRun r where r.finished = true")
    Double averageFinalGrade();

    /** (시작 시각, 끝났는지, 마지막 갱신 시각)만 읽어 날짜별로 자바에서 센다. */
    @Query("select r.createdAt, r.finished, r.updatedAt from ExamCareerRun r where r.createdAt >= :since")
    List<Object[]> runsSince(@Param("since") Instant since);
}
