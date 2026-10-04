package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.PersonalMessageDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PersonalMessageDeliveryRepository extends JpaRepository<PersonalMessageDelivery, Long> {

    List<PersonalMessageDelivery> findAllByDeskAndMessageVisibilityAndMessageStatusNotAndUnlockAtLessThanEqualOrderByCreatedAtDesc(
            PersonalDesk desk,
            MessageVisibility visibility,
            MessageStatus status,
            Instant unlockAt);
}
