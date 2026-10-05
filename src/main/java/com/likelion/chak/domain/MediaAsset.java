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

@Getter
@Entity
@Table(name = "media_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long expectedSize;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant uploadedAt;

    private MediaAsset(
            UserAccount owner,
            MediaPurpose purpose,
            String objectKey,
            String originalFileName,
            String contentType,
            long expectedSize) {
        this.owner = owner;
        this.purpose = purpose;
        this.status = MediaStatus.PENDING;
        this.objectKey = objectKey;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.expectedSize = expectedSize;
    }

    public static MediaAsset pending(
            UserAccount owner,
            MediaPurpose purpose,
            String objectKey,
            String originalFileName,
            String contentType,
            long expectedSize) {
        return new MediaAsset(owner, purpose, objectKey, originalFileName, contentType, expectedSize);
    }

    public void complete(String finalObjectKey, Instant uploadedAt) {
        this.status = MediaStatus.UPLOADED;
        this.objectKey = finalObjectKey;
        this.uploadedAt = uploadedAt;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
