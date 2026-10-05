package com.likelion.chak.service;

import com.likelion.chak.domain.AdEventType;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.TrafficEventType;
import com.likelion.chak.dto.AdvertisementEventRequest;
import com.likelion.chak.dto.AdvertisementRequest;
import com.likelion.chak.dto.AnalyticsSummaryResponse;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.TrafficEventRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AdvertisementAnalyticsServiceTest {

    @Autowired
    private AdvertisementService advertisementService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private GuestIdentityService guestIdentityService;

    @Test
    void activeAdvertisementAndEventsAreStoredAndCounted() {
        Long advertisementId = advertisementService.create(new AdvertisementRequest(
                "수험생 응원 광고",
                "https://cdn.example.com/ad.webp",
                "https://example.com/campaign",
                AdPlacement.DESK,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                true)).id();
        GuestSession guestSession = guestIdentityService.resolveOrCreate(null);
        GuestIdentity guest = guestSession.getGuestIdentity();

        AdvertisementEventRequest adEvent = new AdvertisementEventRequest(
                AdEventType.IMPRESSION, "session-1", null);
        advertisementService.recordEvent(
                advertisementId,
                null,
                guest,
                adEvent);
        advertisementService.recordEvent(advertisementId, null, guest, adEvent);
        TrafficEventRequest trafficEvent = new TrafficEventRequest(
                "event-1",
                TrafficEventType.PAGE_VIEW,
                "session-1",
                "/desk/test",
                null,
                "PERSONAL_DESK",
                "test",
                null);
        analyticsService.record(
                null,
                guest,
                trafficEvent);
        analyticsService.record(null, guest, trafficEvent);

        assertThat(advertisementService.getActive(AdPlacement.DESK)).hasSize(1);
        AnalyticsSummaryResponse summary = analyticsService.summary(
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60));
        assertThat(summary.advertisementEvents()).containsEntry("IMPRESSION", 1L);
        assertThat(summary.trafficEvents()).containsEntry("PAGE_VIEW", 1L);
        assertThat(advertisementService.getMetrics(
                advertisementId,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60)).events())
                .containsEntry("IMPRESSION", 1L);
    }

    @Test
    void rejectsClientAttemptsToRecordServerOnlyTrafficEvents() {
        GuestIdentity guest = guestIdentityService.resolveOrCreate(null).getGuestIdentity();

        assertThatThrownBy(() -> analyticsService.record(
                null,
                guest,
                new TrafficEventRequest(
                        "event-forged",
                        TrafficEventType.MESSAGE_CREATED,
                        "session-1",
                        "/desk/test",
                        null,
                        "PERSONAL_DESK",
                        "test",
                        null)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANALYTICS_EVENT);
    }

    @Test
    void rejectsAdEventsWithoutSessionOrOutsideExposurePeriod() {
        Long inactiveAdvertisementId = advertisementService.create(new AdvertisementRequest(
                "비활성 광고",
                "https://cdn.example.com/ad.webp",
                "https://example.com/campaign",
                AdPlacement.DESK,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                false)).id();
        Long activeAdvertisementId = advertisementService.create(new AdvertisementRequest(
                "활성 광고",
                "https://cdn.example.com/ad.webp",
                "https://example.com/campaign",
                AdPlacement.DESK,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                true)).id();

        assertThatThrownBy(() -> advertisementService.recordEvent(
                inactiveAdvertisementId,
                null,
                null,
                new AdvertisementEventRequest(AdEventType.IMPRESSION, "session-1", null)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANALYTICS_EVENT);
        assertThatThrownBy(() -> advertisementService.recordEvent(
                activeAdvertisementId,
                null,
                null,
                new AdvertisementEventRequest(AdEventType.IMPRESSION, " ", null)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ANALYTICS_EVENT);
    }
}
