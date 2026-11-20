package com.likelion.chak.repository;

import com.likelion.chak.domain.RollingPaperMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RollingPaperMemberRepository extends JpaRepository<RollingPaperMember, Long> {

    /** 반의 모든 사람, 들어온 순서(만든 사람이 맨 앞). */
    List<RollingPaperMember> findAllByPaperClassIdOrderByIdAsc(Long classId);

    Optional<RollingPaperMember> findByPaperClassIdAndUserId(Long classId, Long userId);

    Optional<RollingPaperMember> findByPaperClassIdAndGuestId(Long classId, Long guestId);

    Optional<RollingPaperMember> findByIdAndPaperClassId(Long id, Long classId);

    long countByPaperClassId(Long classId);

    /** 내가 들어간 반, 최근에 들어간 순서. 반은 함께 가져온다. */
    @Query("""
            select m from RollingPaperMember m join fetch m.paperClass
            where (:userId is not null and m.user.id = :userId)
               or (:guestId is not null and m.guest.id = :guestId)
            order by m.id desc
            """)
    List<RollingPaperMember> findAllMine(@Param("userId") Long userId, @Param("guestId") Long guestId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RollingPaperMember m where m.paperClass.id = :classId")
    int deleteAllByClassId(@Param("classId") Long classId);
}
