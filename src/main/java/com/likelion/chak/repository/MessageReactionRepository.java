package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    Optional<MessageReaction> findByMessageIdAndGuestId(Long messageId, Long guestId);

    List<MessageReaction> findAllByUserIdAndMessageIdIn(Long userId, Collection<Long> messageIds);

    List<MessageReaction> findAllByGuestIdAndMessageIdIn(Long guestId, Collection<Long> messageIds);
}
