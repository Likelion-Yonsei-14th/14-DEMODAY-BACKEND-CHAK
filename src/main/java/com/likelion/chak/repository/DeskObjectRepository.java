package com.likelion.chak.repository;

import com.likelion.chak.domain.DeskObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeskObjectRepository extends JpaRepository<DeskObject, Long> {

    Optional<DeskObject> findByMessageId(Long messageId);

    List<DeskObject> findAllByMessageIdIn(Collection<Long> messageIds);
}
