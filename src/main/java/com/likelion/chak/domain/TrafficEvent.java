package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "traffic_events", indexes = {
        @Index(name = "idx_traffic_type_time", columnList = "event_type,created_at"),
        @Index(name = "idx_traffic_user_time", columnList = "user_id,created_at"),
        @Index(name = "idx_traffic_guest_time", columnList = "guest_id,created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrafficEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private GuestIdentity guest;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private TrafficEventType eventType;

    @Column(length = 100)
    private String sessionId;

    @Column(length = 1000)
    private String path;

    @Column(length = 1000)
    private String referrer;

    @Column(length = 100)
    private String resourceType;

    @Column(length = 200)
    private String resourceKey;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String metadataPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private TrafficEvent(
            String eventId,
            UserAccount user,
            GuestIdentity guest,
            TrafficEventType eventType,
            String sessionId,
            String path,
            String referrer,
            String resourceType,
            String resourceKey,
            String metadataPayload) {
        this.eventId = eventId;
        this.user = user;
        this.guest = guest;
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.path = path;
        this.referrer = referrer;
        this.resourceType = resourceType;
        this.resourceKey = resourceKey;
        this.metadataPayload = metadataPayload;
    }

    public static TrafficEvent create(
            String eventId,
            UserAccount user,
            GuestIdentity guest,
            TrafficEventType eventType,
            String sessionId,
            String path,
            String referrer,
            String resourceType,
            String resourceKey,
            String metadataPayload) {
        return new TrafficEvent(
                eventId, user, guest, eventType, sessionId, path, referrer,
                resourceType, resourceKey, metadataPayload);
    }

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }
}
