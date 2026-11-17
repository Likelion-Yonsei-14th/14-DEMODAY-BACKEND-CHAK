package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.AdvertisementRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.AdvertisementMetricsResponse;
import com.likelion.chak.dto.AnalyticsSummaryResponse;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.AdminDtos.DailyStatsResponse;
import com.likelion.chak.dto.AdminDtos.RegionStatsResponse;
import com.likelion.chak.dto.AdminDtos.UserDetail;
import com.likelion.chak.dto.AdminDtos.UserSummary;
import com.likelion.chak.dto.AdminDtos.WithdrawRequest;
import com.likelion.chak.dto.AdminDtos.AdminAccessResponse;
import com.likelion.chak.dto.AdminDtos.GrantAdminRequest;
import com.likelion.chak.service.AdminAccessService;
import com.likelion.chak.service.AdminStatsService;
import com.likelion.chak.service.AdminUserService;
import com.likelion.chak.service.AdvertisementService;
import com.likelion.chak.service.AnalyticsService;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.ReportAdminService;
import com.likelion.chak.domain.ReportStatus;
import com.likelion.chak.dto.CommunityDtos.ReportActionRequest;
import com.likelion.chak.dto.CommunityDtos.ReportResponse;
import com.likelion.chak.dto.SliceResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AuthService authService;
    private final AdvertisementService advertisementService;
    private final AnalyticsService analyticsService;
    private final ReportAdminService reportAdminService;
    private final AdminUserService adminUserService;
    private final AdminStatsService adminStatsService;
    private final com.likelion.chak.service.AdminMinigameStatsService adminMinigameStatsService;
    private final AdminAccessService adminAccessService;
    private final com.likelion.chak.service.PromotionInquiryService promotionInquiryService;
    private final com.likelion.chak.service.BenefitSubmissionService benefitSubmissionService;
    private final com.likelion.chak.service.PollService pollService;
    private final com.likelion.chak.service.AdSwitchService adSwitchService;
    private final com.likelion.chak.service.AdCreativeService adCreativeService;
    private final com.likelion.chak.service.AdReportService adReportService;
    private final com.likelion.chak.service.StoreOutreachService storeOutreachService;

    @GetMapping("/ads")
    public ResponseEntity<List<AdvertisementResponse>> getAdvertisements(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(advertisementService.getAll());
    }

    @PostMapping("/ads")
    public ResponseEntity<AdvertisementResponse> createAdvertisement(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdvertisementRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(advertisementService.create(request));
    }

    @PatchMapping("/ads/{advertisementId}")
    public ResponseEntity<AdvertisementResponse> updateAdvertisement(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("advertisementId") Long advertisementId,
            @Valid @RequestBody AdvertisementRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(advertisementService.update(advertisementId, request));
    }

    /** 광고 전체 노출 스위치. 꺼져 있으면 등록된 광고가 있어도 사용자에게 보이지 않는다. */
    @GetMapping("/ads/switch")
    public ResponseEntity<com.likelion.chak.service.AdSwitchService.State> getAdSwitch(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adSwitchService.state());
    }

    public record AdSwitchRequest(@jakarta.validation.constraints.NotNull Boolean enabled) {
    }

    @org.springframework.web.bind.annotation.PutMapping("/ads/switch")
    public ResponseEntity<com.likelion.chak.service.AdSwitchService.State> setAdSwitch(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdSwitchRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adSwitchService.set(request.enabled(), principal.userId()));
    }

    public record AdCreativeUploaded(Long id, String path) {
    }

    /** 광고 이미지를 원본 그대로(본문 = 파일) 받는다. PNG·JPEG·WEBP·GIF, 1MB까지. */
    @PostMapping(value = "/ads/creatives", consumes = {"image/png", "image/jpeg", "image/webp", "image/gif"})
    public ResponseEntity<AdCreativeUploaded> uploadAdCreative(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody byte[] body) {
        authService.getAdmin(principal.userId());
        long id = adCreativeService.upload(body, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdCreativeUploaded(id, "/api/public/ads/creatives/" + id));
    }

    /** 모든 광고의 지금까지 노출·도달(서로 다른 사람 수)·클릭. */
    @GetMapping("/ads/summary")
    public ResponseEntity<List<com.likelion.chak.service.AdReportService.Summary>> getAdSummaries(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adReportService.summaries());
    }

    /** 한 광고의 기간 내 노출·도달·클릭과 날짜별·지역별 숫자(광고주 보고용). */
    @GetMapping("/ads/{advertisementId}/report")
    public ResponseEntity<com.likelion.chak.service.AdReportService.Report> getAdReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("advertisementId") Long advertisementId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adReportService.report(advertisementId, from, to));
    }

    @GetMapping("/ads/{advertisementId}/metrics")
    public ResponseEntity<AdvertisementMetricsResponse> getAdvertisementMetrics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("advertisementId") Long advertisementId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(advertisementService.getMetrics(advertisementId, from, to));
    }

    @DeleteMapping("/ads/{advertisementId}")
    public ResponseEntity<MessageResponseBody> deactivateAdvertisement(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("advertisementId") Long advertisementId) {
        authService.getAdmin(principal.userId());
        advertisementService.deactivate(advertisementId);
        return ResponseEntity.ok(new MessageResponseBody("광고가 비활성화되었습니다."));
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalyticsSummary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(analyticsService.summary(from, to));
    }

    @GetMapping("/reports")
    public ResponseEntity<SliceResponse<ReportResponse>> getReports(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "status", required = false) ReportStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(reportAdminService.list(status, page, size));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ReportResponse> actOnReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("reportId") Long reportId,
            @Valid @RequestBody ReportActionRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(reportAdminService.act(reportId, request.action()));
    }

    @GetMapping("/users")
    public ResponseEntity<SliceResponse<UserSummary>> getUsers(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "state", defaultValue = "ACTIVE") String state,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminUserService.list(query, state, page, size));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetail> getUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("userId") Long userId) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminUserService.detail(userId));
    }

    @PostMapping("/users/{userId}/withdraw")
    public ResponseEntity<UserDetail> withdrawUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) WithdrawRequest request) {
        authService.getAdmin(principal.userId());
        boolean confirm = request != null && request.confirmForfeitBalance();
        return ResponseEntity.ok(adminUserService.withdraw(principal.userId(), userId, confirm));
    }

    @GetMapping("/stats/daily")
    public ResponseEntity<DailyStatsResponse> getDailyStats(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "from", required = false) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) java.time.LocalDate to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminStatsService.daily(from, to));
    }

    @GetMapping("/stats/active")
    public ResponseEntity<com.likelion.chak.dto.AdminDtos.ActiveUsersResponse> getActiveUsers(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "from", required = false) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) java.time.LocalDate to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminStatsService.active(from, to));
    }

    @GetMapping("/stats/regions")
    public ResponseEntity<RegionStatsResponse> getRegionStats(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "from", required = false) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) java.time.LocalDate to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminStatsService.regions(from, to));
    }

    @GetMapping("/minigames/stats")
    public ResponseEntity<com.likelion.chak.dto.AdminDtos.MinigameStatsResponse> getMinigameStats(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminMinigameStatsService.stats());
    }

    @GetMapping("/admins")
    public ResponseEntity<AdminAccessResponse> getAdmins(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminAccessService.list());
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminAccessResponse> grantAdmin(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody GrantAdminRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminAccessService.grant(principal.userId(), request.email()));
    }

    @DeleteMapping("/admins/users/{userId}")
    public ResponseEntity<AdminAccessResponse> revokeAdmin(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("userId") Long userId) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminAccessService.revoke(principal.userId(), userId));
    }

    @DeleteMapping("/admins/invites/{inviteId}")
    public ResponseEntity<AdminAccessResponse> cancelAdminInvite(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("inviteId") Long inviteId) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminAccessService.cancelInvite(principal.userId(), inviteId));
    }

    @GetMapping("/polls")
    public ResponseEntity<List<com.likelion.chak.dto.PollDtos.AdminPollSummary>> getPolls(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(pollService.adminList(Instant.now()));
    }

    @PostMapping("/polls")
    public ResponseEntity<com.likelion.chak.dto.PollDtos.AdminPollSummary> createPoll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody com.likelion.chak.dto.PollDtos.AdminPollRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pollService.create(request, Instant.now()));
    }

    @PatchMapping("/polls/{pollId}")
    public ResponseEntity<com.likelion.chak.dto.PollDtos.AdminPollSummary> updatePoll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long pollId,
            @RequestBody com.likelion.chak.dto.PollDtos.AdminPollUpdate request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(pollService.update(pollId, request, Instant.now()));
    }

    @GetMapping("/promotion-inquiries")
    public ResponseEntity<SliceResponse<com.likelion.chak.dto.PromotionDtos.InquiryResponse>> getPromotionInquiries(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "status", required = false) com.likelion.chak.domain.PromotionInquiry.Status status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(promotionInquiryService.list(status, page, size));
    }

    @PatchMapping("/promotion-inquiries/{inquiryId}")
    public ResponseEntity<com.likelion.chak.dto.PromotionDtos.InquiryResponse> changePromotionInquiryStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody com.likelion.chak.dto.PromotionDtos.InquiryStatusRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(promotionInquiryService.changeStatus(principal.userId(), inquiryId, request.status()));
    }

    @GetMapping("/benefit-submissions")
    public ResponseEntity<SliceResponse<com.likelion.chak.dto.BenefitDtos.SubmissionSummary>> getBenefitSubmissions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "status", required = false) com.likelion.chak.domain.PromotionInquiry.Status status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(benefitSubmissionService.list(status, page, size));
    }

    @GetMapping("/benefit-submissions/{submissionId}")
    public ResponseEntity<com.likelion.chak.dto.BenefitDtos.SubmissionDetail> getBenefitSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("submissionId") Long submissionId) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(benefitSubmissionService.detail(submissionId));
    }

    @PatchMapping("/benefit-submissions/{submissionId}")
    public ResponseEntity<com.likelion.chak.dto.BenefitDtos.SubmissionSummary> changeBenefitSubmissionStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("submissionId") Long submissionId,
            @Valid @RequestBody com.likelion.chak.dto.BenefitDtos.StatusRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(benefitSubmissionService.changeStatus(principal.userId(), submissionId, request.status()));
    }

    @GetMapping("/store-outreach")
    public ResponseEntity<List<com.likelion.chak.dto.OutreachDtos.OutreachResponse>> getStoreOutreach(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(storeOutreachService.list());
    }

    @PostMapping("/store-outreach")
    public ResponseEntity<com.likelion.chak.dto.OutreachDtos.OutreachResponse> createStoreOutreach(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody com.likelion.chak.dto.OutreachDtos.OutreachRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(storeOutreachService.create(principal.userId(), request));
    }

    @org.springframework.web.bind.annotation.PutMapping("/store-outreach/{outreachId}")
    public ResponseEntity<com.likelion.chak.dto.OutreachDtos.OutreachResponse> updateStoreOutreach(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("outreachId") Long outreachId,
            @Valid @RequestBody com.likelion.chak.dto.OutreachDtos.OutreachRequest request) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(storeOutreachService.update(principal.userId(), outreachId, request));
    }

    @DeleteMapping("/store-outreach/{outreachId}")
    public ResponseEntity<Void> deleteStoreOutreach(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("outreachId") Long outreachId) {
        authService.getAdmin(principal.userId());
        storeOutreachService.delete(principal.userId(), outreachId);
        return ResponseEntity.noContent().build();
    }
}
