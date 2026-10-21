package com.likelion.chak.repository;

import com.likelion.chak.domain.AdminInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminInviteRepository extends JpaRepository<AdminInvite, Long> {

    Optional<AdminInvite> findByEmail(String email);

    List<AdminInvite> findAllByOrderByCreatedAtDesc();
}
