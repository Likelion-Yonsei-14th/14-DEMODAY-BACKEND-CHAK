package com.likelion.chak.repository;

import com.likelion.chak.domain.TrafficEvent;
import com.likelion.chak.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrafficEventRepository extends JpaRepository<TrafficEvent, Long> {

    Optional<TrafficEvent> findByEventId(String eventId);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant createdAt);

    long countByGuestIdAndCreatedAtGreaterThanEqual(Long guestId, Instant createdAt);

    @Modifying
    @Query(value = """
            INSERT INTO traffic_events (
                event_id, user_id, guest_id, event_type, session_id, path, referrer,
                resource_type, resource_key, metadata_payload, country, region, created_at
            ) VALUES (
                :eventId, :userId, :guestId, :eventType, :sessionId, :path, :referrer,
                :resourceType, :resourceKey, :metadataPayload, :country, :region, CURRENT_TIMESTAMP(6)
            ) ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertOrKeepExisting(
            @Param("eventId") String eventId,
            @Param("userId") Long userId,
            @Param("guestId") Long guestId,
            @Param("eventType") String eventType,
            @Param("sessionId") String sessionId,
            @Param("path") String path,
            @Param("referrer") String referrer,
            @Param("resourceType") String resourceType,
            @Param("resourceKey") String resourceKey,
            @Param("metadataPayload") String metadataPayload,
            @Param("country") String country,
            @Param("region") String region);

    /** 기간 안의 페이지 조회(접속자 통계용). 행: 시각, 회원 id, 비회원 id, 국가, 시·도. */
    @Query("""
            select event.createdAt, event.user.id, event.guest.id, event.country, event.region
            from TrafficEvent event
            left join event.user member
            where event.eventType = com.likelion.chak.domain.TrafficEventType.PAGE_VIEW
              and event.createdAt >= :from and event.createdAt < :to
              and (member.id is null or member.provider <> :excludedProvider)
            """)
    List<Object[]> findPageViewsBetween(@Param("from") Instant from, @Param("to") Instant to,
                                        @Param("excludedProvider") AuthProvider excludedProvider);

    /** Test-only region events, never mixed into real visitor dashboards. */
    @Query("""
            select event.region, event.resourceType, count(event)
            from TrafficEvent event
            where event.eventType = com.likelion.chak.domain.TrafficEventType.PAGE_VIEW
              and event.createdAt >= :from and event.createdAt < :to
              and event.user.provider = :provider
              and event.resourceType in ('LOAD_TEST_MEMBER', 'LOAD_TEST_GUEST')
            group by event.region, event.resourceType
            """)
    List<Object[]> countLoadTestRegions(@Param("from") Instant from, @Param("to") Instant to,
                                        @Param("provider") AuthProvider provider);

    @Query("""
            select event.eventType, count(event)
            from TrafficEvent event
            left join event.user member
            where event.createdAt >= :from and event.createdAt < :to
              and (member.id is null or member.provider <> :excludedProvider)
            group by event.eventType
            """)
    List<Object[]> countByEventTypeBetween(@Param("from") Instant from, @Param("to") Instant to,
                                           @Param("excludedProvider") AuthProvider excludedProvider);
}
