package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

public interface PersonalMessageDeliveryRepository extends JpaRepository<PersonalMessageDelivery, Long> {

    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    Slice<PersonalMessageDelivery> findAllByDeskAndMessageVisibilityAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDescIdDesc(
            PersonalDesk desk,
            MessageVisibility visibility,
            MessageStatus status,
            Instant unlockAt,
            Pageable pageable);

    /**
     * 받은 편지 목록. basketMode: ALL(전체), IN(바구니 안), OUT(바구니 밖).
     * unlockedBefore가 지정되면 그 시각까지 열린 편지만 조회한다.
     */
    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    @Query("""
            select d from PersonalMessageDelivery d
            where d.desk = :desk
              and d.message.status <> :deleted
              and d.unlockAt <= :unlockedBefore
              and (:basketMode = 'ALL'
                   or (:basketMode = 'IN' and d.basketedAt is not null)
                   or (:basketMode = 'OUT' and d.basketedAt is null))
            order by d.createdAt desc, d.id desc
            """)
    Slice<PersonalMessageDelivery> findOwnerDeliveries(
            @Param("desk") PersonalDesk desk,
            @Param("deleted") MessageStatus deleted,
            @Param("unlockedBefore") Instant unlockedBefore,
            @Param("basketMode") String basketMode,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"message"})
    Optional<PersonalMessageDelivery> findForUpdateByIdAndDesk(Long id, PersonalDesk desk);
}
