package com.likelion.chak.controller;

import com.likelion.chak.alert.InquiryRateLimiter;
import com.likelion.chak.alert.UploadRateLimiter;
import com.likelion.chak.dto.BenefitDtos;
import com.likelion.chak.dto.PromotionDtos.InquiryReceived;
import com.likelion.chak.dto.PromotionDtos.InquiryRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.BenefitSubmissionService;
import com.likelion.chak.service.PromotionInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 브랜드 담당자가 /promotion/apply에서 남기는 광고 상담 신청과 혜택 시안 신청. 로그인 없이 호출한다. */
@RestController
@RequestMapping("/api/public/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionInquiryService inquiryService;
    private final InquiryRateLimiter rateLimiter;
    private final BenefitSubmissionService benefitService;
    private final UploadRateLimiter uploadRateLimiter;

    @PostMapping("/inquiries")
    public ResponseEntity<InquiryReceived> submit(@Valid @RequestBody InquiryRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(ClientIp.of(http))) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        inquiryService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new InquiryReceived(true));
    }

    /** 제휴 업체가 /promotion/apply의 혜택 시안 신청서에서 올릴 파일(로고·코드 목록·매장 목록)의 주소를 받는다. */
    @PostMapping("/benefit-uploads")
    public ResponseEntity<BenefitDtos.UploadResponse> upload(@Valid @RequestBody BenefitDtos.UploadRequest request, HttpServletRequest http) {
        if (!uploadRateLimiter.tryAcquire(ClientIp.of(http))) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(benefitService.createUpload(request));
    }

    @PostMapping("/benefit-submissions")
    public ResponseEntity<BenefitDtos.Received> submitBenefit(@Valid @RequestBody BenefitDtos.SubmitRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(ClientIp.of(http))) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        benefitService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BenefitDtos.Received(true));
    }
}
