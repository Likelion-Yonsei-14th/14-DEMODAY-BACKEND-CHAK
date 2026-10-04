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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "desk_objects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeskObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desk_id", nullable = false)
    private PersonalDesk desk;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeskObjectType representationType;

    @Column(length = 100)
    private String assetId;

    @Column(length = 30)
    private String color;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double rotation;

    @Column(nullable = false)
    private double scale;

    @Column(nullable = false)
    private int zIndex;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String metadataPayload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private DeskObject(
            PersonalDesk desk,
            Message message,
            DeskObjectType representationType,
            String assetId,
            String color,
            double x,
            double y,
            double rotation,
            double scale,
            int zIndex,
            String metadataPayload) {
        this.desk = desk;
        this.message = message;
        this.representationType = representationType;
        this.assetId = assetId;
        this.color = color;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.scale = scale;
        this.zIndex = zIndex;
        this.metadataPayload = metadataPayload;
    }

    public static DeskObject create(
            PersonalDesk desk,
            Message message,
            DeskObjectType representationType,
            String assetId,
            String color,
            double x,
            double y,
            double rotation,
            double scale,
            int zIndex,
            String metadataPayload) {
        return new DeskObject(
                desk,
                message,
                representationType,
                assetId,
                color,
                x,
                y,
                rotation,
                scale,
                zIndex,
                metadataPayload);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
