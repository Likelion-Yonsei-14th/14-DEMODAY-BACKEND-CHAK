package com.likelion.chak.repository;

import com.likelion.chak.domain.D1CourseResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface D1CourseResponseRepository extends JpaRepository<D1CourseResponse, Long> {

    Optional<D1CourseResponse> findByResponseId(String responseId);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countBySharedTrue();

    interface KeyCount {
        String getKey();

        Long getCount();
    }

    @Query("select r.area as key, count(r) as count from D1CourseResponse r group by r.area")
    List<KeyCount> countByArea();

    @Query("select r.companion as key, count(r) as count from D1CourseResponse r group by r.companion")
    List<KeyCount> countByCompanion();

    @Query("select r.budget as key, count(r) as count from D1CourseResponse r group by r.budget")
    List<KeyCount> countByBudget();

    @Query("select r.startRegion as key, count(r) as count from D1CourseResponse r group by r.startRegion")
    List<KeyCount> countByRegion();

    @Query("select r.activity1 as key, count(r) as count from D1CourseResponse r group by r.activity1")
    List<KeyCount> countByActivity1();

    @Query("select r.activity2 as key, count(r) as count from D1CourseResponse r group by r.activity2")
    List<KeyCount> countByActivity2();

    @Query("select r.activity3 as key, count(r) as count from D1CourseResponse r group by r.activity3")
    List<KeyCount> countByActivity3();
}
