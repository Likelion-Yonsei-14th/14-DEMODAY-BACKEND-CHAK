package com.likelion.chak.repository;

import com.likelion.chak.domain.DeskBlock;
import com.likelion.chak.domain.PersonalDesk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeskBlockRepository extends JpaRepository<DeskBlock, Long> {

    List<DeskBlock> findAllByDesk(PersonalDesk desk);

    Optional<DeskBlock> findByIdAndDesk(Long id, PersonalDesk desk);

    Optional<DeskBlock> findByDeskAndAuthorUserId(PersonalDesk desk, Long userId);

    Optional<DeskBlock> findByDeskAndAuthorGuestId(PersonalDesk desk, Long guestId);
}
