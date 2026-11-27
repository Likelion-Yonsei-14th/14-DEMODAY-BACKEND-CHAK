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
import com.likelion.chak.domain.AdPlacement;

public interface AdvertisementEventRepository extends JpaRepository<AdvertisementEvent, Long> {

    Optional<AdvertisementEvent> findByAdvertisementIdAndEventTypeAndSessionId(
            Long advertisementId,
            AdEventType eventType,
            String sessionId);

    boolean existsByEventTypeAndSessionIdAndUserId(AdEventType eventType, String sessionId, Long userId);

    boolean existsByEventTypeAndSessionIdAndGuestId(AdEventType eventType, String sessionId, Long guestId);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant createdAt);

    long countByAdvertisementIdAndEventTypeAndUserIdAndCreatedAtGreaterThanEqual(
            Long advertisementId, AdEventType eventType, Long userId, Instant createdAt);

    long countByAdvertisementIdAndEventTypeAndGuestIdAndCreatedAtGreaterThanEqual(
            Long advertisementId, AdEventType eventType, Long guestId, Instant createdAt);

    boolean existsByAdvertisementIdAndEventTypeAndSessionIdAndUserId(
            Long advertisementId, AdEventType eventType, String sessionId, Long userId);

    boolean existsByAdvertisementIdAndEventTypeAndSessionIdAndGuestId(
            Long advertisementId, AdEventType eventType, String sessionId, Long guestId);

    /** 해제 광고(UNLOCK 자리) 한 회차의 시작·완료 기록. */
    Optional<AdvertisementEvent> findFirstByEventTypeAndSessionIdAndUserIdAndAdvertisementPlacement(
            AdEventType eventType, String sessionId, Long userId, AdPlacement placement);

    Optional<AdvertisementEvent> findFirstByEventTypeAndSessionIdAndGuestIdAndAdvertisementPlacement(
            AdEventType eventType, String sessionId, Long guestId, AdPlacement placement);

    long countByGuestIdAndCreatedAtGreaterThanEqual(Long guestId, Instant createdAt);

    @Modifying
    @Query(value = """
            INSERT INTO advertisement_events (
                advertisement_id, user_id, guest_id, event_type, session_id,
                view_duration_ms, region, created_at
            ) VALUES (
                :advertisementId, :userId, :guestId, :eventType, :sessionId,
                :viewDurationMs, :region, CURRENT_TIMESTAMP(6)
            ) ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertOrKeepExisting(
            @Param("advertisementId") Long advertisementId,
            @Param("userId") Long userId,
            @Param("guestId") Long guestId,
            @Param("eventType") String eventType,
            @Param("sessionId") String sessionId,
            @Param("viewDurationMs") Long viewDurationMs,
            @Param("region") String region);

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

    /** 한 사람(회원 또는 손님)이 since 이후 광고별로 본 노출 수. */
    @Query("""
            select event.advertisement.id, count(event)
            from AdvertisementEvent event
            where event.eventType = com.likelion.chak.domain.AdEventType.IMPRESSION
              and (event.user.id = :userId or event.guest.id = :guestId)
              and event.createdAt >= :since
            group by event.advertisement.id
            """)
    List<Object[]> countImpressionsByViewerSince(
            @Param("userId") Long userId,
            @Param("guestId") Long guestId,
            @Param("since") Instant since);

    /** 보고서용 원자료: 종류, 회원 번호, 손님 번호, 시·도, 시각. */
    @Query("""
            select event.eventType, event.user.id, event.guest.id, event.region, event.createdAt
            from AdvertisementEvent event
            where event.advertisement.id = :advertisementId
              and event.eventType in (com.likelion.chak.domain.AdEventType.IMPRESSION, com.likelion.chak.domain.AdEventType.CLICK)
              and event.createdAt >= :from and event.createdAt < :to
            """)
    List<Object[]> findReportRows(
            @Param("advertisementId") Long advertisementId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
