package com.likelion.chak.repository;

import com.likelion.chak.domain.AdvertisementEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.likelion.chak.domain.AdEventType;

public interface AdvertisementEventRepository extends JpaRepository<AdvertisementEvent, Long> {

    Optional<AdvertisementEvent> findByAdvertisementIdAndEventTypeAndSessionId(
            Long advertisementId,
            AdEventType eventType,
            String sessionId);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant createdAt);

    long countByGuestIdAndCreatedAtGreaterThanEqual(Long guestId, Instant createdAt);

    @Modifying
    @Query(value = """
            INSERT INTO advertisement_events (
                advertisement_id, user_id, guest_id, event_type, session_id,
                view_duration_ms, created_at
            ) VALUES (
                :advertisementId, :userId, :guestId, :eventType, :sessionId,
                :viewDurationMs, CURRENT_TIMESTAMP(6)
            ) ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertOrKeepExisting(
            @Param("advertisementId") Long advertisementId,
            @Param("userId") Long userId,
            @Param("guestId") Long guestId,
            @Param("eventType") String eventType,
            @Param("sessionId") String sessionId,
            @Param("viewDurationMs") Long viewDurationMs);

    @Query("""
            select event.eventType, count(event)
            from AdvertisementEvent event
            where event.createdAt >= :from and event.createdAt < :to
            group by event.eventType
            """)
    List<Object[]> countByEventTypeBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select event.eventType, count(event)
            from AdvertisementEvent event
            where event.advertisement.id = :advertisementId
              and event.createdAt >= :from and event.createdAt < :to
            group by event.eventType
            """)
    List<Object[]> countByAdvertisementAndEventTypeBetween(
            @Param("advertisementId") Long advertisementId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
