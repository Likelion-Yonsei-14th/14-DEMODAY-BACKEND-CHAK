package com.likelion.chak.repository;

import com.likelion.chak.domain.RollingPaperBoardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RollingPaperBoardEntryRepository extends JpaRepository<RollingPaperBoardEntry, Long> {

    List<RollingPaperBoardEntry> findAllByPaperClassIdOrderByIdAsc(Long classId);

    long countByPaperClassId(Long classId);

    Optional<RollingPaperBoardEntry> findByIdAndPaperClassId(Long id, Long classId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RollingPaperBoardEntry b where b.paperClass.id = :classId")
    int deleteAllByClassId(@Param("classId") Long classId);
}
