package com.likelion.chak.service;

import com.likelion.chak.alert.AlertMessage;
import com.likelion.chak.alert.AlertSanitizer;
import com.likelion.chak.alert.ErrorAlertService;
import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.PromotionInquiry;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.PromotionDtos.InquiryRequest;
import com.likelion.chak.dto.PromotionDtos.InquiryResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.PromotionInquiryRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** 광고 상담 신청 접수와 운영자 확인. 접수하면 운영자 디스코드에 알리되 연락처는 알림에 싣지 않는다. */
@Service
@RequiredArgsConstructor
public class PromotionInquiryService {

    private final PromotionInquiryRepository inquiryRepository;
    private final UserAccountRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ErrorAlertService alertService;

    /**
     * 문의를 저장한다. 자동 입력(숨은 칸이 채워짐)이나 같은 회사·이메일의 24시간 안 재전송은 저장하지 않고 성공으로 돌려
     * 반복 전송이나 스팸 프로그램에 단서를 주지 않는다.
     */
    @Transactional
    public void submit(InquiryRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String company = request.company().strip();
        if (inquiryRepository.existsByEmailIgnoreCaseAndCompanyIgnoreCaseAndCreatedAtAfter(
                email, company, Instant.now().minus(Duration.ofHours(24)))) {
            return;
        }
        PromotionInquiry inquiry = inquiryRepository.save(new PromotionInquiry(
                company,
                request.contactName().strip(),
                email,
                blankToNull(request.phone()),
                blankToNull(request.category()),
                request.interest(),
                blankToNull(request.message())));

        alertService.alert(AlertMessage.builder("문의", "새 광고 상담 신청", "promotion-inquiry|" + inquiry.getId(),
                        AlertMessage.Severity.WARN)
                .description("어드민 > 상담 문의에서 확인하세요.")
                .field("회사", AlertSanitizer.clean(company, 100))
                .field("업종", AlertSanitizer.clean(inquiry.getCategory(), 50))
                .field("관심", inquiry.getInterest() == null ? null : inquiry.getInterest().name())
                .build());
    }

    @Transactional(readOnly = true)
    public SliceResponse<InquiryResponse> list(PromotionInquiry.Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Slice<PromotionInquiry> slice = status == null
                ? inquiryRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size))
                : inquiryRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status, PageRequest.of(page, size));
        return SliceResponse.from(slice, slice.getContent().stream().map(InquiryResponse::from).toList());
    }

    @Transactional
    public InquiryResponse changeStatus(Long adminId, Long inquiryId, PromotionInquiry.Status status) {
        UserAccount admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        PromotionInquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));
        inquiry.changeStatus(status, Instant.now());
        auditLogRepository.save(new AdminAuditLog(admin, "PROMOTION_INQUIRY_STATUS", "PROMOTION_INQUIRY", inquiryId, status.name()));
        return InquiryResponse.from(inquiry);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
