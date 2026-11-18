package com.likelion.chak.repository;

import com.likelion.chak.domain.DiscountEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DiscountEventRepository extends JpaRepository<DiscountEvent, Long> {

    /** 이 사람이 since 이후 본 횟수(혜택별). 회원·손님 중 없는 쪽은 -1로 넘겨 어떤 행과도 맞지 않게 한다. */
    @Query("select e.discountId, count(e) from DiscountEvent e where e.eventType = com.likelion.chak.domain.DiscountEvent.Type.IMPRESSION "
            + "and (e.userId = :userId or e.guestId = :guestId) and e.createdAt >= :since group by e.discountId")
    List<Object[]> countImpressionsByViewerSince(@Param("userId") Long userId, @Param("guestId") Long guestId,
                                                 @Param("since") Instant since);

    @Query("select count(e) > 0 from DiscountEvent e where e.discountId = :discountId and e.eventType = :type "
            + "and (e.userId = :userId or e.guestId = :guestId) and e.createdAt >= :since")
    boolean existsByViewerSince(@Param("discountId") Long discountId, @Param("type") DiscountEvent.Type type,
                                @Param("userId") Long userId, @Param("guestId") Long guestId, @Param("since") Instant since);

    /** 집계용 원자료: 혜택·종류·사람·시각. */
    @Query("select e.discountId, e.eventType, e.userId, e.guestId, e.createdAt from DiscountEvent e where e.createdAt >= :from and e.createdAt < :to")
    List<Object[]> findRows(@Param("from") Instant from, @Param("to") Instant to);
}
