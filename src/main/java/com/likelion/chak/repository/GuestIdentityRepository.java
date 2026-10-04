package com.likelion.chak.repository;

import com.likelion.chak.domain.GuestIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestIdentityRepository extends JpaRepository<GuestIdentity, Long> {

    Optional<GuestIdentity> findByGuestKey(String guestKey);
}
