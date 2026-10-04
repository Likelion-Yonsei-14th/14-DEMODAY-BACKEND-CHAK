package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "personal_message_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalMessageDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desk_id", nullable = false)
    private PersonalDesk desk;

    @Column(nullable = false)
    private Instant unlockAt;

    @Column(nullable = false)
    private boolean preclaimBacklog;

    private Instant basketedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private PersonalMessageDelivery(
            Message message,
            PersonalDesk desk,
            Instant unlockAt,
            boolean preclaimBacklog) {
        this.message = message;
        this.desk = desk;
        this.unlockAt = unlockAt;
        this.preclaimBacklog = preclaimBacklog;
    }

    public static PersonalMessageDelivery create(
            Message message,
            PersonalDesk desk,
            Instant unlockAt,
            boolean preclaimBacklog) {
        return new PersonalMessageDelivery(message, desk, unlockAt, preclaimBacklog);
    }

    public boolean isUnlocked(Instant now) {
        return !unlockAt.isAfter(now);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
