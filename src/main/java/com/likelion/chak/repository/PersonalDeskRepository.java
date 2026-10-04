package com.likelion.chak.repository;

import com.likelion.chak.domain.PersonalDesk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalDeskRepository extends JpaRepository<PersonalDesk, Long> {

    Optional<PersonalDesk> findBySupporterToken(String supporterToken);
}
