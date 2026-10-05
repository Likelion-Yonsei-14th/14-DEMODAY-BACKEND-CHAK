package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    Slice<PersonalMessageDelivery> findAllByDeskAndMessageStatusNotOrderByCreatedAtDescIdDesc(
            PersonalDesk desk,
            MessageStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    Slice<PersonalMessageDelivery> findAllByDeskAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDescIdDesc(
            PersonalDesk desk,
            MessageStatus status,
            Instant unlockAt,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"message"})
    Optional<PersonalMessageDelivery> findForUpdateByIdAndDesk(Long id, PersonalDesk desk);
}
