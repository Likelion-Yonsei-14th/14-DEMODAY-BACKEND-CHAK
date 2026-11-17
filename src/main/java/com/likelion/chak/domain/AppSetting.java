package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영 설정 한 줄. 읽고 쓰는 일은 {@code AdSwitchService}가 SQL로 하고, 이 클래스는 표 모양을 정한다. */
@Getter
@Entity
@Table(name = "app_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
