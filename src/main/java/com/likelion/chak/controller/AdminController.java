package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.AdvertisementRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.AdvertisementMetricsResponse;
import com.likelion.chak.dto.AnalyticsSummaryResponse;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.service.AdvertisementService;
import com.likelion.chak.service.AnalyticsService;
import com.likelion.chak.service.AuthService;
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
}
