package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.TrafficEventType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.dto.AdminDtos.DailyStat;
import com.likelion.chak.dto.AdminDtos.DailyStatsResponse;
import com.likelion.chak.dto.AdminDtos.RegionStatsResponse;
import com.likelion.chak.dto.TrafficEventRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AdminStatsServiceTest {

    @Autowired private AdminStatsService statsService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private GuestIdentityService guestIdentityService;
    @Autowired private UserAccountRepository userRepository;

    private GuestIdentity guest() {
        return guestIdentityService.resolveOrCreate(null).getGuestIdentity();
    }

    private TrafficEventRequest pageView(String path) {
        return new TrafficEventRequest(UUID.randomUUID().toString(), TrafficEventType.PAGE_VIEW,
                "s-" + UUID.randomUUID(), path, null, null, null, null);
    }

    @Test
    void dailyStatsCountDistinctVisitorsAndPageViewsInKoreanTime() {
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        GuestIdentity guestA = guest();
        GuestIdentity guestB = guest();

        analyticsService.record(member, null, pageView("/home"));
        analyticsService.record(member, null, pageView("/start"));
        analyticsService.record(null, guestA, pageView("/start"));
        analyticsService.record(null, guestB, pageView("/start"));
        analyticsService.record(null, guestB, pageView("/home"));

        LocalDate today = LocalDate.now(AdminStatsService.KST);
        DailyStatsResponse stats = statsService.daily(today, today);

        DailyStat day = stats.days().get(0);
        assertThat(day.date()).isEqualTo(today);
        assertThat(day.pageViews()).isEqualTo(5);
        assertThat(day.memberVisitors()).isEqualTo(1);
        assertThat(day.guestVisitors()).isEqualTo(2);
        assertThat(day.visitors()).isEqualTo(3);
        assertThat(day.signups()).isGreaterThanOrEqualTo(1);
        assertThat(stats.totalUsers()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void daysWithoutActivityAreStillListed() {
        LocalDate end = LocalDate.now(AdminStatsService.KST).minusDays(30);
        DailyStatsResponse stats = statsService.daily(end.minusDays(2), end);

        assertThat(stats.days()).hasSize(3);
        assertThat(stats.days()).allSatisfy(d -> assertThat(d.visitors()).isZero());
    }

    @Test
    void testVisitsIncreaseVisitorStatsWithoutCountingAsSignups() {
        LocalDate today = LocalDate.now(AdminStatsService.KST);
        DailyStatsResponse before = statsService.daily(today, today);
        UserAccount testUser = userRepository.save(UserAccount.create(AuthProvider.TEST, "loadtest:" + UUID.randomUUID(), "부하시험", null, null));
        analyticsService.record(testUser, null, pageView("/home"));
        DailyStatsResponse after = statsService.daily(today, today);
        assertThat(after.totalUsers()).isEqualTo(before.totalUsers());
        assertThat(after.days().get(0).signups()).isEqualTo(before.days().get(0).signups());
        assertThat(after.days().get(0).memberVisitors()).isEqualTo(before.days().get(0).memberVisitors() + 1);
        assertThat(after.days().get(0).pageViews()).isEqualTo(before.days().get(0).pageViews() + 1);
    }

    @Test
    void markedLoadTestGuestsCountPerVisitEvenIfAnOlderRunReusedItsCookie() {
        LocalDate today = LocalDate.now(AdminStatsService.KST);
        long beforeGuests = statsService.daily(today, today).days().get(0).guestVisitors();
        long beforeSeoul = statsService.regions(today, today).items().stream()
                .filter(item -> "11".equals(item.region())).mapToLong(item -> item.visitors()).sum();
        GuestIdentity sharedGuest = guest();
        for (int i = 0; i < 2; i++) {
            var visit = new TrafficEventRequest(UUID.randomUUID().toString(), TrafficEventType.PAGE_VIEW,
                    "load-test-guest-" + i, "/", null, "LOAD_TEST_GUEST", null, null);
            analyticsService.record(null, sharedGuest, visit, new AnalyticsService.Geo("KR", "11"));
        }
        assertThat(statsService.daily(today, today).days().get(0).guestVisitors()).isEqualTo(beforeGuests + 2);
        long afterSeoul = statsService.regions(today, today).items().stream()
                .filter(item -> "11".equals(item.region())).mapToLong(item -> item.visitors()).sum();
        assertThat(afterSeoul).isEqualTo(beforeSeoul + 2);
    }

    @Test
    void rangesAreValidated() {
        LocalDate today = LocalDate.now(AdminStatsService.KST);
        assertThatThrownBy(() -> statsService.daily(today, today.minusDays(1))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> statsService.daily(today.minusDays(400), today)).isInstanceOf(CustomException.class);
    }

    @Test
    void regionStatsGroupByProvinceAndCountUnknown() {
        GuestIdentity seoulGuest = guest();
        GuestIdentity busanGuest = guest();
        GuestIdentity unknownGuest = guest();
        analyticsService.record(null, seoulGuest, pageView("/a"), new AnalyticsService.Geo("KR", "11"));
        analyticsService.record(null, seoulGuest, pageView("/b"), new AnalyticsService.Geo("KR", "11"));
        analyticsService.record(null, busanGuest, pageView("/a"), new AnalyticsService.Geo("KR", "26"));
        analyticsService.record(null, unknownGuest, pageView("/a"), null);

        LocalDate today = LocalDate.now(AdminStatsService.KST);
        RegionStatsResponse regions = statsService.regions(today, today);

        assertThat(regions.unknownPageViews()).isGreaterThanOrEqualTo(1);
        var seoul = regions.items().stream().filter(i -> "서울".equals(i.regionName())).findFirst().orElseThrow();
        assertThat(seoul.pageViews()).isEqualTo(2);
        assertThat(seoul.visitors()).isEqualTo(1);
        assertThat(regions.items()).anyMatch(i -> "부산".equals(i.regionName()));
        assertThat(regions.items().get(0).regionName()).isEqualTo("서울");
    }

    @Test
    void overseasVisitsAreLeftOutOfTheRegionTableAndCountedSeparately() {
        LocalDate today = LocalDate.now(AdminStatsService.KST);
        long before = statsService.regions(today, today).overseasPageViews();
        analyticsService.record(null, guest(), pageView("/a"), new AnalyticsService.Geo("US", "CA"));
        analyticsService.record(null, guest(), pageView("/a"), new AnalyticsService.Geo("JP", "13"));
        analyticsService.record(null, guest(), pageView("/a"), new AnalyticsService.Geo("KR", "49"));

        RegionStatsResponse regions = statsService.regions(today, today);

        assertThat(regions.overseasPageViews() - before).isEqualTo(2);
        assertThat(regions.items()).allMatch(i -> "KR".equals(i.country()));
        assertThat(regions.items()).anyMatch(i -> "제주".equals(i.regionName()));
        assertThat(regions.items()).noneMatch(i -> "CA".equals(i.regionName()));
    }

    @Test
    void regionNamesFallBackToTheRawCode() {
        assertThat(RegionNames.of("KR", "11")).isEqualTo("서울");
        assertThat(RegionNames.of("KR", "KR-41")).isEqualTo("경기");
        assertThat(RegionNames.of("KR", "99")).isEqualTo("99");
        assertThat(RegionNames.of("US", "CA")).isEqualTo("CA");
        assertThat(RegionNames.of("JP", null)).isEqualTo("JP");
        assertThat(RegionNames.of(null, null)).isEqualTo("알 수 없음");
    }

    @Test
    void geoHeadersAreValidatedAndOnlyCodesAreKept() {
        assertThat(AnalyticsService.Geo.fromHeaders("kr", "11")).isEqualTo(new AnalyticsService.Geo("KR", "11"));
        assertThat(AnalyticsService.Geo.fromHeaders("KOR", "<script>")).isNull();
        assertThat(AnalyticsService.Geo.fromHeaders(null, null)).isNull();
        assertThat(AnalyticsService.Geo.fromHeaders("KR", "x".repeat(60))).isEqualTo(new AnalyticsService.Geo("KR", null));
    }
}
