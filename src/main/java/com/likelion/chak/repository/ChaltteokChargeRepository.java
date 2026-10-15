package com.likelion.chak.repository;

import com.likelion.chak.domain.ChaltteokCharge;
import com.likelion.chak.domain.ChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChaltteokChargeRepository extends JpaRepository<ChaltteokCharge, Long> {

    Optional<ChaltteokCharge> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChaltteokCharge> findForUpdateByOrderId(String orderId);

    long countByUserIdAndStatusAndCreatedAtAfter(Long userId, ChargeStatus status, Instant after);

    /**
     * 승인 요청 선점(compare-and-set). READY이거나, 선점한 지 오래된(요청이 죽은) CONFIRMING일 때만 1을 돌려준다.
     * 같은 주문의 동시 승인 요청 중 정확히 하나만 성공한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChaltteokCharge c set c.status = com.likelion.chak.domain.ChargeStatus.CONFIRMING, c.claimedAt = :now "
            + "where c.orderId = :orderId and c.user.id = :userId "
            + "and (c.status = com.likelion.chak.domain.ChargeStatus.READY "
            + "or (c.status = com.likelion.chak.domain.ChargeStatus.CONFIRMING and c.claimedAt < :staleBefore))")
    int claim(@Param("orderId") String orderId, @Param("userId") Long userId,
              @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    /** 승인에 실패했을 때 선점을 풀어 같은 주문으로 다시 결제할 수 있게 한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChaltteokCharge c set c.status = com.likelion.chak.domain.ChargeStatus.READY "
            + "where c.orderId = :orderId and c.status = com.likelion.chak.domain.ChargeStatus.CONFIRMING")
    int release(@Param("orderId") String orderId);
}
