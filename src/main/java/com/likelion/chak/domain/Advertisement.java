package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "advertisements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String creativeUrl;

    @Column(nullable = false, length = 1000)
    private String destinationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdPlacement placement;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active;

    /** 대상 시·도 이름을 쉼표로("서울,경기"). 비어 있으면 전국. */
    @Column(name = "target_regions", length = 200)
    private String targetRegions;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Advertisement(
            String title,
            String creativeUrl,
            String destinationUrl,
            AdPlacement placement,
            Instant startsAt,
            Instant endsAt,
            boolean active) {
        update(title, creativeUrl, destinationUrl, placement, startsAt, endsAt, active);
    }

    public static Advertisement create(
            String title,
            String creativeUrl,
            String destinationUrl,
            AdPlacement placement,
            Instant startsAt,
            Instant endsAt,
            boolean active) {
        return new Advertisement(title, creativeUrl, destinationUrl, placement, startsAt, endsAt, active);
    }

    public void update(
            String title,
            String creativeUrl,
            String destinationUrl,
            AdPlacement placement,
            Instant startsAt,
            Instant endsAt,
            boolean active) {
        this.title = title;
        this.creativeUrl = creativeUrl;
        this.destinationUrl = destinationUrl;
        this.placement = placement;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public void updateTargetRegions(java.util.List<String> regions) {
        this.targetRegions = regions == null || regions.isEmpty() ? null : String.join(",", regions);
    }

    /** 대상 시·도 이름 목록. 비어 있으면 전국. */
    public java.util.List<String> targetRegionList() {
        return targetRegions == null || targetRegions.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.stream(targetRegions.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    /** 이 지역 사람에게 보여 줄 수 있는지. 지역을 모르는 사람에게는 전국 광고만. */
    public boolean targets(String regionName) {
        java.util.List<String> regions = targetRegionList();
        return regions.isEmpty() || (regionName != null && regions.contains(regionName));
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isEligible(Instant now) {
        return active && !startsAt.isAfter(now) && endsAt.isAfter(now);
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
