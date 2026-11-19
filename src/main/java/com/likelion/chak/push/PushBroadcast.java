package com.likelion.chak.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 한 번만 보내는 일괄 알림을 이미 보냈다는 기록. */
@Getter
@Entity
@Table(name = "push_broadcasts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushBroadcast {

    @Id
    @Column(name = "broadcast_key", length = 64)
    private String key;

    @Column(nullable = false)
    private Instant sentAt;
}
