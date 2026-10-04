package com.likelion.chak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "guest_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String guestKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private GuestIdentity(String guestKey) {
        this.guestKey = guestKey;
    }

    public static GuestIdentity create() {
        return new GuestIdentity(UUID.randomUUID().toString());
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
