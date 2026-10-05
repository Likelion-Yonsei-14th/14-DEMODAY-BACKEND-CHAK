package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "personal_desks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalDesk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", unique = true)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private UserAccount creator;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus claimStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadModeType readModeType;

    private LocalTime dailyUnlockTime;

    private Instant capsuleUnlockAt;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Column(nullable = false)
    private boolean publicFeedEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PRIVATE'")
    private MessageVisibility defaultMessageVisibility;

    @Column(nullable = false)
    private boolean roomClosed;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String supporterToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private PersonalDesk(
            UserAccount owner,
            UserAccount creator,
            String displayName,
            ClaimStatus claimStatus,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        this.owner = owner;
        this.creator = creator;
        this.displayName = displayName;
        this.claimStatus = claimStatus;
        this.readModeType = readModeType;
        this.dailyUnlockTime = dailyUnlockTime;
        this.capsuleUnlockAt = capsuleUnlockAt;
        this.timezone = "Asia/Seoul";
        this.publicFeedEnabled = true;
        this.defaultMessageVisibility = MessageVisibility.PRIVATE;
        this.roomClosed = false;
        this.supporterToken = UUID.randomUUID().toString();
    }

    public static PersonalDesk createClaimed(
            UserAccount owner,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        return new PersonalDesk(
                owner,
                owner,
                displayName,
                ClaimStatus.CLAIMED,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);
    }

    public static PersonalDesk createUnclaimed(
            UserAccount creator,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        return new PersonalDesk(
                null,
                creator,
                displayName,
                ClaimStatus.UNCLAIMED,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);
    }

    public void closeRoom() {
        this.roomClosed = true;
    }

    public void openRoom() {
        this.roomClosed = false;
    }

    public void updateSettings(
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt,
            boolean publicFeedEnabled,
            MessageVisibility defaultMessageVisibility) {
        this.displayName = displayName;
        this.readModeType = readModeType;
        this.dailyUnlockTime = dailyUnlockTime;
        this.capsuleUnlockAt = capsuleUnlockAt;
        this.publicFeedEnabled = publicFeedEnabled;
        this.defaultMessageVisibility = defaultMessageVisibility;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
