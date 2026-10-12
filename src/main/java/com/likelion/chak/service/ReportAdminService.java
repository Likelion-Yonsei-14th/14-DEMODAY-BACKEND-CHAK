package com.likelion.chak.service;

import com.likelion.chak.domain.MessageReport;
import com.likelion.chak.domain.ReportStatus;
import com.likelion.chak.dto.CommunityDtos.ReportAction;
import com.likelion.chak.dto.CommunityDtos.ReportResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MessageReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportAdminService {

    private final MessageReportRepository reportRepository;

    @Transactional(readOnly = true)
    public SliceResponse<ReportResponse> list(ReportStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Slice<MessageReport> reports = reportRepository.findAllByStatusOrderByCreatedAtDescIdDesc(
                status == null ? ReportStatus.OPEN : status, PageRequest.of(page, size));
        return SliceResponse.from(reports, reports.getContent().stream().map(this::toResponse).toList());
    }

    /** DISMISS는 신고만 기각하고, REMOVE_MESSAGE는 편지를 삭제 처리한 뒤 신고를 해결로 표시한다. */
    @Transactional
    public ReportResponse act(Long reportId, ReportAction action) {
        MessageReport report = reportRepository.findWithMessageById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
        Instant now = Instant.now();
        if (action == ReportAction.REMOVE_MESSAGE) {
            report.getMessage().delete(now);
            report.resolve(ReportStatus.RESOLVED, now);
        } else {
            report.resolve(ReportStatus.DISMISSED, now);
        }
        return toResponse(report);
    }

    private ReportResponse toResponse(MessageReport report) {
        var message = report.getMessage();
        return new ReportResponse(
                report.getId(),
                message.getId(),
                report.getReason(),
                report.getStatus(),
                message.getAuthorDisplayName(),
                message.getVisibility(),
                message.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt());
    }
}
