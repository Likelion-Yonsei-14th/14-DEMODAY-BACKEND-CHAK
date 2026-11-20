package com.likelion.chak.repository;

import com.likelion.chak.domain.RollingPaperClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RollingPaperClassRepository extends JpaRepository<RollingPaperClass, Long> {

    Optional<RollingPaperClass> findByCode(String code);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RollingPaperClass c where c.id = :id")
    int deleteByIdNow(@Param("id") Long id);
}
