package com.likelion.chak.repository;

import com.likelion.chak.domain.RollingPaperNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RollingPaperNoteRepository extends JpaRepository<RollingPaperNote, Long> {

    List<RollingPaperNote> findAllByPaperIdOrderByIdAsc(Long paperMemberId);

    long countByPaperId(Long paperMemberId);

    Optional<RollingPaperNote> findByPaperIdAndAuthorId(Long paperMemberId, Long authorMemberId);

    /** 내가 한마디를 남긴 종이(주인) id들. */
    @Query("select n.paper.id from RollingPaperNote n where n.author.id = :authorId")
    List<Long> findPaperIdsWrittenBy(@Param("authorId") Long authorMemberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RollingPaperNote n where n.paperClass.id = :classId")
    int deleteAllByClassId(@Param("classId") Long classId);
}
