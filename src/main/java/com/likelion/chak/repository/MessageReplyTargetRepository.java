package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageReply;
import com.likelion.chak.domain.MessageReplyTarget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageReplyTargetRepository extends JpaRepository<MessageReplyTarget, Long> {

    @EntityGraph(attributePaths = {"message"})
    List<MessageReplyTarget> findAllByReplyIn(Collection<MessageReply> replies);

    List<MessageReplyTarget> findAllByMessageIdIn(Collection<Long> messageIds);

    /** 작성자 본인 편지에 달린 답장 대상. */
    @EntityGraph(attributePaths = {"reply", "reply.desk", "reply.desk.owner", "message"})
    @Query("""
            select t from MessageReplyTarget t
            where (:userId is not null and t.message.authorUser.id = :userId)
               or (:guestId is not null and t.message.authorGuest.id = :guestId)
            order by t.reply.createdAt desc, t.id desc
            """)
    List<MessageReplyTarget> findAllForAuthor(@Param("userId") Long userId, @Param("guestId") Long guestId);
}
