package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "message_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private UserAccount reporterUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_guest_id")
    private GuestIdentity reporterGuest;

    @Column(length = 100)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    private MessageReport(Message message, UserAccount user, GuestIdentity guest, String reason) {
        this.message = message;
        this.reporterUser = user;
        this.reporterGuest = guest;
        this.reason = reason;
        this.status = ReportStatus.OPEN;
    }

    public static MessageReport create(Message message, UserAccount user, GuestIdentity guest, String reason) {
        return new MessageReport(message, user, guest, reason);
    }

    public void resolve(ReportStatus status, Instant at) {
        this.status = status;
        this.resolvedAt = at;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
