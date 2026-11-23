package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.AdDeliveryService;
import com.likelion.chak.service.AdSwitchService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 광고 고르기(같은 노출·지역 맞춤·사람별 돌려 보기)와 도달 보고. 다른 테스트와 섞이지 않게 MESSAGE_COMPLETE 지면을 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdDeliveryApiTest {

    private static final String PLACEMENT = "MESSAGE_COMPLETE";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private AdSwitchService adSwitchService;
    @Autowired private AdDeliveryService adDeliveryService;

    private String admin;

    @BeforeEach
    void setUp() {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        user.updateAdmin(true);
        userRepository.save(user);
        admin = "Bearer " + jwt.createAccessToken(user);
        adSwitchService.set(true, null);
        adDeliveryService.invalidateCounts();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE advertisements SET active = FALSE WHERE placement = ?", PLACEMENT);
        adSwitchService.set(false, null);
        adDeliveryService.invalidateCounts();
    }

    private long createAd(String title, String regionsJson) throws Exception {
        Instant now = Instant.now();
        String body = "{\"title\":\"" + title + "\",\"creativeUrl\":\"https://cdn.example.com/a.png\","
                + "\"destinationUrl\":\"https://example.com\",\"placement\":\"" + PLACEMENT + "\","
                + "\"startsAt\":\"" + now.minus(1, ChronoUnit.HOURS) + "\",\"endsAt\":\"" + now.plus(1, ChronoUnit.DAYS) + "\","
                + "\"active\":true" + (regionsJson == null ? "" : ",\"targetRegions\":" + regionsJson) + "}";
        MvcResult res = mockMvc.perform(post("/api/admin/ads").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long serve(String regionCode, Cookie guest) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/public/ads/serve").param("placement", PLACEMENT);
        if (regionCode != null) {
            req = req.header("X-Vercel-IP-Country", "KR").header("X-Vercel-IP-Country-Region", regionCode);
        }
        if (guest != null) {
            req = req.cookie(guest);
        }
        MvcResult res = mockMvc.perform(req).andReturn();
        if (res.getResponse().getStatus() == 204) {
            return null;
        }
        return mapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    /** 노출을 남기고, 손님 쿠키를 돌려준다(처음이면 새로 받는다). */
    private Cookie impression(long adId, String type, Cookie guest, String regionCode) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/public/ads/" + adId + "/events")
                .header("X-Forwarded-For", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"" + type + "\",\"sessionId\":\"" + UUID.randomUUID() + "\"}");
        if (regionCode != null) {
            req = req.header("X-Vercel-IP-Country", "KR").header("X-Vercel-IP-Country-Region", regionCode);
        }
        if (guest != null) {
            req = req.cookie(guest);
        }
        MvcResult res = mockMvc.perform(req).andExpect(status().isCreated()).andReturn();
        Cookie issued = res.getResponse().getCookie("chak_guest_id");
        return issued != null ? issued : guest;
    }

    @Test
    void spreadsServingEvenlyAcrossAds() throws Exception {
        long a = createAd("A", null);
        long b = createAd("B", null);
        long c = createAd("C", null);
        Map<Long, Integer> served = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            served.merge(serve("11", null), 1, Integer::sum);
        }
        assertThat(served).containsOnlyKeys(a, b, c);
        assertThat(served.values()).allSatisfy(count -> assertThat(count).isEqualTo(10));
    }

    @Test
    void catchesUpTheAdWithFewerRecordedImpressions() throws Exception {
        long a = createAd("A", null);
        long b = createAd("B", null);
        for (int i = 0; i < 3; i++) {
            impression(a, "IMPRESSION", null, "11");
        }
        adDeliveryService.invalidateCounts();
        assertThat(serve("11", null)).isEqualTo(b);
        assertThat(serve("11", null)).isEqualTo(b);
    }

    @Test
    void keepsOtherRegionsAdsOutWhereARegionalAdMatches() throws Exception {
        long seoul = createAd("서울 매장", "[\"서울\"]");
        long busan = createAd("부산 매장", "[\"부산\"]");
        long nationwide = createAd("전국", null);
        Map<Long, Integer> inBusan = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            inBusan.merge(serve("26", null), 1, Integer::sum);
        }
        assertThat(inBusan).containsOnlyKeys(busan, nationwide);
        assertThat(inBusan).doesNotContainKey(seoul);
    }

    @Test
    void showsSeoulAdsToGyeonggiAndIncheonVisitorsButNotTheOtherWay() throws Exception {
        long seoul = createAd("서울 매장", "[\"서울\"]");
        long gyeonggi = createAd("경기 매장", "[\"경기\"]");
        long busan = createAd("부산 매장", "[\"부산\"]");
        Map<Long, Integer> inGyeonggi = new HashMap<>();
        Map<Long, Integer> inIncheon = new HashMap<>();
        Map<Long, Integer> inSeoul = new HashMap<>();
        // 노출 수를 고르게 맞추므로, 다른 지역 노출이 섞이지 않게 지역마다 따로 돌린다.
        for (int i = 0; i < 10; i++) {
            inGyeonggi.merge(serve("41", null), 1, Integer::sum);
        }
        for (int i = 0; i < 4; i++) {
            inIncheon.merge(serve("28", null), 1, Integer::sum);
            inSeoul.merge(serve("11", null), 1, Integer::sum);
        }
        // 외곽(경기·인천)에서도 서울로 오기 쉬워 서울 광고가 함께 돈다.
        assertThat(inGyeonggi).containsOnlyKeys(seoul, gyeonggi);
        assertThat(inGyeonggi.values()).allSatisfy(count -> assertThat(count).isEqualTo(5));
        assertThat(inIncheon).containsOnlyKeys(seoul);
        // 서울 방문자에게 경기 광고를, 어디에도 부산 광고를 섞지 않는다.
        assertThat(inSeoul).containsOnlyKeys(seoul);
        assertThat(inGyeonggi).doesNotContainKey(busan);
    }

    @Test
    void mixesRegionalAdsInWhenOnlyANationwideAdWouldMatch() throws Exception {
        long busan = createAd("부산 매장", "[\"부산\"]");
        long daegu = createAd("대구 매장", "[\"대구\"]");
        long nationwide = createAd("전국", null);
        // 서울(지역 광고 없음)과 위치 모름 손님에게 전국 광고 하나만 계속 나가지 않고 고르게 돈다.
        Map<Long, Integer> served = new HashMap<>();
        for (int i = 0; i < 15; i++) {
            served.merge(serve(i % 2 == 0 ? "11" : null, null), 1, Integer::sum);
        }
        assertThat(served).containsOnlyKeys(busan, daegu, nationwide);
        assertThat(served.values()).allSatisfy(count -> assertThat(count).isEqualTo(5));
    }

    @Test
    void rotatesSoTheSameViewerSeesAnAdTheyHaveNotSeenToday() throws Exception {
        long a = createAd("A", null);
        long b = createAd("B", null);
        // 다른 사람들이 B를 많이 봐서 B의 전체 노출이 더 많아도
        for (int i = 0; i < 5; i++) {
            impression(b, "IMPRESSION", null, "11");
        }
        Cookie viewer = impression(a, "IMPRESSION", null, "11");
        adDeliveryService.invalidateCounts();
        // 오늘 A를 이미 본 사람에게는 B를 먼저 보여 준다
        assertThat(serve("11", viewer)).isEqualTo(b);
    }

    @Test
    void servesNothingWhileTheSwitchIsOff() throws Exception {
        createAd("A", null);
        adSwitchService.set(false, null);
        assertThat(serve("11", null)).isNull();
    }

    @Test
    void reportsImpressionsReachClicksByDayAndRegion() throws Exception {
        long a = createAd("A", null);
        Cookie first = impression(a, "IMPRESSION", null, "11");
        impression(a, "IMPRESSION", first, "11"); // 같은 사람 두 번
        Cookie second = impression(a, "IMPRESSION", null, "26");
        impression(a, "CLICK", second, "26");

        JsonNode report = mapper.readTree(mockMvc.perform(get("/api/admin/ads/" + a + "/report").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.at("/totals/impressions").asLong()).isEqualTo(3);
        assertThat(report.at("/totals/reach").asLong()).isEqualTo(2);
        assertThat(report.at("/totals/clicks").asLong()).isEqualTo(1);
        assertThat(report.get("days")).hasSize(1);
        Map<String, Long> reach = new HashMap<>();
        report.get("regions").forEach(r -> reach.put(r.get("region").asText(), r.get("reach").asLong()));
        assertThat(reach).containsEntry("서울", 1L).containsEntry("부산", 1L);

        JsonNode summaries = mapper.readTree(mockMvc.perform(get("/api/admin/ads/summary").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode s : summaries) {
            if (s.get("advertisementId").asLong() == a) mine = s;
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("reach").asLong()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownTargetRegions() throws Exception {
        Instant now = Instant.now();
        String body = "{\"title\":\"X\",\"creativeUrl\":\"https://cdn.example.com/a.png\",\"destinationUrl\":\"https://example.com\","
                + "\"placement\":\"" + PLACEMENT + "\",\"startsAt\":\"" + now + "\",\"endsAt\":\"" + now.plus(1, ChronoUnit.DAYS)
                + "\",\"active\":true,\"targetRegions\":[\"서울\",\"평양\"]}";
        mockMvc.perform(post("/api/admin/ads").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
