package com.likelion.chak.repository;

import com.likelion.chak.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.MessageStatus;
import java.time.Instant;

public interface MessageRepository extends JpaRepository<Message, Long> {

    long countByAuthorUserIdAndStatusNot(Long userId, MessageStatus status);

    long countByStatusNot(MessageStatus status);

    @Query("select message.createdAt from Message message where message.createdAt >= :from and message.createdAt < :to")
    java.util.List<Instant> findCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** 탈퇴한 회원이 쓴 응원의 표시 이름을 바꾼다(응원은 남기고 작성자만 익명으로). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message message set message.nicknameOverride = :name where message.authorUser.id = :userId")
    int anonymizeAuthor(@Param("userId") Long userId, @Param("name") String name);

    Slice<Message> findAllByAuthorUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long userId, MessageStatus status, Pageable pageable);

    Slice<Message> findAllByAuthorGuestIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long guestId, MessageStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Message message
            set message.visibility = :visibility,
                message.updatedAt = :cutoff
            where message.status <> :deletedStatus
              and message.id in (
                select delivery.message.id
                from PersonalMessageDelivery delivery
                where delivery.desk.id = :deskId
                  and delivery.createdAt <= :cutoff
              )
            """)
    int updateVisibilityByDeskAndCreatedAtLessThanEqual(
            @Param("deskId") Long deskId,
            @Param("visibility") MessageVisibility visibility,
            @Param("deletedStatus") MessageStatus deletedStatus,
            @Param("cutoff") Instant cutoff);
}
