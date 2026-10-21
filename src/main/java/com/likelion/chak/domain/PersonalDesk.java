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
    private boolean pushEnabled = true;

    @Column(nullable = false)
    private boolean roomClosed;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String supporterToken;

    /** 넘겨받기 전에만 값이 있다. 이 링크를 받은 사람이 수령자로 책상을 가져간다. */
    @Column(unique = true, length = 36)
    private String claimToken;

    private Instant claimedAt;

    /** 값이 있으면 삭제된 책상이다. 응원 기록은 남기지만 어디서도 조회되지 않는다. */
    private Instant deletedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** 주인이 탈퇴했을 때: 새 응원을 받지 않게 닫고 주인 이름을 지운다. 받은 응원은 그대로 남는다. */
    public void closeForWithdrawnOwner() {
        this.roomClosed = true;
        this.displayName = UserAccount.WITHDRAWN_NAME;
    }

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
        if (claimStatus == ClaimStatus.UNCLAIMED) {
            this.claimToken = UUID.randomUUID().toString();
        }
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 수령자가 책상을 넘겨받는다. 링크는 즉시 무효가 되고, 만든 사람의 관리 권한은 끝난다. */
    public void claim(UserAccount newOwner, Instant at) {
        this.owner = newOwner;
        this.claimStatus = ClaimStatus.CLAIMED;
        this.claimToken = null;
        this.claimedAt = at;
    }

    /** 현재 주인(넘겨받기 전에는 만든 사람)이 책상을 지운다. 주인 연결을 풀어 새 책상을 만들 수 있게 한다. */
    public void softDelete(Instant at) {
        this.deletedAt = at;
        this.owner = null;
        this.claimToken = null;
        this.roomClosed = true;
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

    public void changePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
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
