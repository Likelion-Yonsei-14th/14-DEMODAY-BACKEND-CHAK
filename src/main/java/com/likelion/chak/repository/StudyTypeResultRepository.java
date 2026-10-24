package com.likelion.chak.repository;

import com.likelion.chak.domain.StudyTypeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudyTypeResultRepository extends JpaRepository<StudyTypeResult, Long> {

    Optional<StudyTypeResult> findByUserId(Long userId);

    interface CodeCount {
        String getCode();

        Long getCount();
    }

    @Query("select s.code as code, count(s) as count from StudyTypeResult s group by s.code")
    List<CodeCount> countByCode();
}
