package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영자가 올린 광고 이미지. 읽고 쓰는 일은 {@code AdCreativeService}가 SQL로 하고, 이 클래스는 표 모양을 정한다. */
@Getter
@Entity
@Table(name = "ad_creatives")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdCreative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    /** MySQL LONGBLOB. 길이를 크게 잡아야 검증 때 tinyblob로 보지 않는다. */
    @Lob
    @Column(nullable = false, length = org.hibernate.Length.LONG32)
    private byte[] data;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
