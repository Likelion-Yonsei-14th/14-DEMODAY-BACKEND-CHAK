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
    private final AdminAccessService adminAccessService;

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

    @GetMapping("/stats/regions")
    public ResponseEntity<RegionStatsResponse> getRegionStats(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "from", required = false) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) java.time.LocalDate to) {
        authService.getAdmin(principal.userId());
        return ResponseEntity.ok(adminStatsService.regions(from, to));
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
}
