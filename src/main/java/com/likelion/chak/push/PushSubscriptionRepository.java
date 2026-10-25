package com.likelion.chak.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserIdIn(java.util.Collection<Long> userIds);

    List<PushSubscription> findAllByGuestIdIn(java.util.Collection<Long> guestIds);

    long countByUserId(Long userId);

    long countByGuestId(Long guestId);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.userId = :userId")
    int deleteAllByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(String endpoint);
}
