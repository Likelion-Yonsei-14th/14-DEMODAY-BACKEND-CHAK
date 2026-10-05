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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "advertisement_events", indexes = {
        @Index(name = "idx_ad_events_ad_type_time", columnList = "advertisement_id,event_type,created_at"),
        @Index(name = "idx_ad_events_user_time", columnList = "user_id,created_at"),
        @Index(name = "idx_ad_events_guest_time", columnList = "guest_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_ad_event_session_type",
                columnNames = {"advertisement_id", "event_type", "session_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvertisementEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertisement_id", nullable = false)
    private Advertisement advertisement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private GuestIdentity guest;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AdEventType eventType;

    @Column(nullable = false, length = 100)
    private String sessionId;

    private Long viewDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private AdvertisementEvent(
            Advertisement advertisement,
            UserAccount user,
            GuestIdentity guest,
            AdEventType eventType,
            String sessionId,
            Long viewDurationMs) {
        this.advertisement = advertisement;
        this.user = user;
        this.guest = guest;
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.viewDurationMs = viewDurationMs;
    }

    public static AdvertisementEvent create(
            Advertisement advertisement,
            UserAccount user,
            GuestIdentity guest,
            AdEventType eventType,
            String sessionId,
            Long viewDurationMs) {
        return new AdvertisementEvent(
                advertisement, user, guest, eventType, sessionId, viewDurationMs);
    }

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }
}
