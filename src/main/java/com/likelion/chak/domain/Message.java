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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private UserAccount authorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_guest_id")
    private GuestIdentity authorGuest;

    @Column(length = 50)
    private String nicknameOverride;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(nullable = false)
    private int schemaVersion;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String cardPayload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant readAt;

    private Instant deletedAt;

    private Message(
            UserAccount authorUser,
            GuestIdentity authorGuest,
            String nicknameOverride,
            MessageKind kind,
            MessageVisibility visibility,
            int schemaVersion,
            String cardPayload) {
        this.authorUser = authorUser;
        this.authorGuest = authorGuest;
        this.nicknameOverride = nicknameOverride;
        this.kind = kind;
        this.visibility = visibility;
        this.status = MessageStatus.SENT;
        this.schemaVersion = schemaVersion;
        this.cardPayload = cardPayload;
    }

    public static Message createByGuest(
            GuestIdentity authorGuest,
            String nickname,
            MessageKind kind,
            MessageVisibility visibility,
            int schemaVersion,
            String cardPayload) {
        return new Message(
                null,
                authorGuest,
                nickname,
                kind,
                visibility,
                schemaVersion,
                cardPayload);
    }

    public static Message createByUser(
            UserAccount authorUser,
            String nicknameOverride,
            MessageKind kind,
            MessageVisibility visibility,
            int schemaVersion,
            String cardPayload) {
        return new Message(
                authorUser,
                null,
                normalizeNickname(nicknameOverride),
                kind,
                visibility,
                schemaVersion,
                cardPayload);
    }

    public String getAuthorDisplayName() {
        if (nicknameOverride != null && !nicknameOverride.isBlank()) {
            return nicknameOverride;
        }
        if (authorUser != null) {
            return authorUser.getDisplayName();
        }
        return "익명의 친구";
    }

    public void markRead(Instant readAt) {
        if (status == MessageStatus.DELETED) {
            return;
        }
        this.status = MessageStatus.READ;
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    public void changeVisibility(MessageVisibility visibility) {
        if (status != MessageStatus.DELETED) {
            this.visibility = visibility;
        }
    }

    public void delete(Instant deletedAt) {
        this.status = MessageStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
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
