package com.likelion.chak.repository;

import com.likelion.chak.domain.MessageReport;
import com.likelion.chak.domain.ReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageReportRepository extends JpaRepository<MessageReport, Long> {

    boolean existsByMessageIdAndReporterUserId(Long messageId, Long userId);

    boolean existsByMessageIdAndReporterGuestId(Long messageId, Long guestId);

    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    Slice<MessageReport> findAllByStatusOrderByCreatedAtDescIdDesc(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"message", "message.authorUser"})
    Optional<MessageReport> findWithMessageById(Long id);
}
