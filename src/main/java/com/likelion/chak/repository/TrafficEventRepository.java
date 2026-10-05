package com.likelion.chak.repository;

import com.likelion.chak.domain.TrafficEvent;
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
                resource_type, resource_key, metadata_payload, created_at
            ) VALUES (
                :eventId, :userId, :guestId, :eventType, :sessionId, :path, :referrer,
                :resourceType, :resourceKey, :metadataPayload, CURRENT_TIMESTAMP(6)
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
            @Param("metadataPayload") String metadataPayload);

    @Query("""
            select event.eventType, count(event)
            from TrafficEvent event
            where event.createdAt >= :from and event.createdAt < :to
            group by event.eventType
            """)
    List<Object[]> countByEventTypeBetween(@Param("from") Instant from, @Param("to") Instant to);
}
