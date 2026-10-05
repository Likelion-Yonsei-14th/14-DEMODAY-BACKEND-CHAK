package com.likelion.chak.repository;

import com.likelion.chak.domain.PersonalDesk;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalDeskRepository extends JpaRepository<PersonalDesk, Long> {

    Optional<PersonalDesk> findBySupporterToken(String supporterToken);

    Optional<PersonalDesk> findByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PersonalDesk> findForUpdateBySupporterToken(String supporterToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PersonalDesk> findForUpdateByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
