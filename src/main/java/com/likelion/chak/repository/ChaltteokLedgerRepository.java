package com.likelion.chak.repository;

import com.likelion.chak.domain.ChaltteokLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChaltteokLedgerRepository extends JpaRepository<ChaltteokLedgerEntry, Long> {

    Slice<ChaltteokLedgerEntry> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<ChaltteokLedgerEntry> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** 장부 합계. 지갑 잔액과 항상 같아야 하며, 다르면 정합성 오류다. */
    @Query("select coalesce(sum(e.amount), 0) from ChaltteokLedgerEntry e where e.user.id = :userId")
    long sumAmountByUserId(@Param("userId") Long userId);
}
