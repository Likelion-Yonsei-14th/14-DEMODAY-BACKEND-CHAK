package com.likelion.chak.controller;

import com.jayway.jsonpath.JsonPath;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.DiscountAdService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiscountAdApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private DiscountAdService discountAdService;

    private String auth;

    @BeforeEach
    void admin() {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        auth = "Bearer " + jwt.createAccessToken(admin);
    }

    private long create(String brand, String area, boolean sponsored, String location) throws Exception {
        String body = "{\"brand\":\"" + brand + "\",\"category\":\"뷰티\",\"benefit\":\"수험생 30% 할인\","
                + "\"area\":\"" + area + "\",\"sourceUrl\":\"https://www.example.com/" + brand + "\","
                + "\"visible\":true,\"sponsored\":" + sponsored + location + "}";
        String res = mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private List<Long> order(MockHttpServletRequestBuilder req) throws Exception {
        String res = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(res, "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private MvcResult event(long id, String type, Cookie guest) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/public/discounts/" + id + "/events")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\",\"placement\":\"LIST\"}");
        if (guest != null) req = req.cookie(guest);
        return mockMvc.perform(req).andReturn();
    }

    @Test
    void sponsoredDiscountsComeNearestFirstThenByRegion() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        // 강남역 근처, 부산 서면, 전국. 제휴가 아닌 혜택은 순서에 안 나온다.
        long gangnam = create("강남" + tag, "서울", true, ",\"address\":\"서울 강남구\",\"latitude\":37.4979,\"longitude\":127.0276");
        long busan = create("서면" + tag, "부산", true, ",\"latitude\":35.1577,\"longitude\":129.0591");
        long nation = create("전국" + tag, "전국", true, "");
        long plain = create("일반" + tag, "서울", false, "");

        List<Long> near = order(get("/api/public/discounts/sponsored").param("lat", "37.50").param("lng", "127.03"));
        assertThat(near).doesNotContain(plain);
        assertThat(near.indexOf(gangnam)).isLessThan(near.indexOf(busan));
        mockMvc.perform(get("/api/public/discounts/sponsored").param("lat", "37.50").param("lng", "127.03"))
                .andExpect(jsonPath("$.located").value(true))
                .andExpect(jsonPath("$.items[?(@.id==" + gangnam + ")].distanceKm").value(org.hamcrest.Matchers.contains(0.3)))
                .andExpect(jsonPath("$.items[?(@.id==" + nation + ")].distanceKm").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

        // 위치 없이 부산에서 접속하면 부산 혜택 → 전국 혜택 → 서울 혜택.
        List<Long> fromBusan = order(get("/api/public/discounts/sponsored")
                .header("X-Chak-Geo-Country", "KR").header("X-Chak-Geo-Region", "26"));
        assertThat(fromBusan.indexOf(busan)).isLessThan(fromBusan.indexOf(nation));
        assertThat(fromBusan.indexOf(nation)).isLessThan(fromBusan.indexOf(gangnam));

        // 제휴 표시와 위치는 공개 목록에도 실린다.
        mockMvc.perform(get("/api/public/discounts"))
                .andExpect(jsonPath("$.items[?(@.id==" + gangnam + ")].sponsored").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.items[?(@.id==" + gangnam + ")].latitude").value(org.hamcrest.Matchers.contains(37.4979)));
    }

    @Test
    void nationwideAdDoesNotAlwaysTopWhenNothingIsNearby() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        long nation = create("전국많이" + tag, "전국", true, "");
        long regional = create("제주덜" + tag, "제주", true, "");
        for (int i = 0; i < 3; i++) {
            event(nation, "IMPRESSION", null); // 전국 혜택은 이미 많이 보였다
        }
        discountAdService.invalidateCounts();

        // 그 지역(강원) 혜택이 없을 때: 노출이 적은 지역 혜택이 전국 혜택보다 먼저.
        List<Long> fromGangwon = order(get("/api/public/discounts/sponsored")
                .header("X-Chak-Geo-Country", "KR").header("X-Chak-Geo-Region", "51"));
        assertThat(fromGangwon.indexOf(regional)).isLessThan(fromGangwon.indexOf(nation));

        // 내 위치를 알려 줬는데 30km 안에 아무것도 없을 때(울릉도)도 마찬가지.
        List<Long> farAway = order(get("/api/public/discounts/sponsored").param("lat", "37.48").param("lng", "130.90"));
        assertThat(farAway.indexOf(regional)).isLessThan(farAway.indexOf(nation));
    }

    @Test
    void sameViewerSeesLessSeenAdsFirstAndAdsEvenOut() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        long a = create("가" + tag, "제주", true, "");
        long b = create("나" + tag, "제주", true, "");
        // 제주에서 접속한 손님이 a를 봤다 → 같은 손님에게는 b가 a보다 먼저.
        Cookie guest = event(a, "IMPRESSION", null).getResponse().getCookie("chak_guest_id");
        assertThat(guest).isNotNull();
        event(a, "IMPRESSION", null); // 다른 손님도 a를 봤다: a 노출 2
        discountAdService.invalidateCounts();
        List<Long> mine = order(get("/api/public/discounts/sponsored").cookie(guest)
                .header("X-Chak-Geo-Country", "KR").header("X-Chak-Geo-Region", "49"));
        assertThat(mine.indexOf(b)).isLessThan(mine.indexOf(a));

        // 처음 온 사람에게도, 노출이 적은 b가 먼저(위에서 b를 맨 위에 한 번 올렸어도 1 < 2).
        List<Long> fresh = order(get("/api/public/discounts/sponsored")
                .header("X-Chak-Geo-Country", "KR").header("X-Chak-Geo-Region", "49"));
        assertThat(fresh.indexOf(b)).isLessThan(fresh.indexOf(a));
    }

    @Test
    void impressionsAndClicksAreCountedForAdmins() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        long ad = create("집계" + tag, "서울", true, "");
        long plain = create("비제휴" + tag, "서울", false, "");

        MvcResult first = event(ad, "IMPRESSION", null);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        Cookie viewer = first.getResponse().getCookie("chak_guest_id");
        // 같은 사람이 30분 안에 다시 보면 한 번으로 친다.
        assertThat(event(ad, "IMPRESSION", viewer).getResponse().getStatus()).isEqualTo(202);
        // 다른 사람
        Cookie other = event(ad, "IMPRESSION", null).getResponse().getCookie("chak_guest_id");
        assertThat(event(ad, "CLICK", viewer).getResponse().getStatus()).isEqualTo(201);
        assertThat(event(ad, "CLICK", viewer).getResponse().getStatus()).isEqualTo(202);
        assertThat(event(ad, "CLICK", other).getResponse().getStatus()).isEqualTo(201);
        // 제휴가 아닌 혜택은 세지 않는다.
        assertThat(event(plain, "CLICK", viewer).getResponse().getStatus()).isEqualTo(202);

        mockMvc.perform(get("/api/admin/discounts/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/discounts/stats").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + ad + ")].impressions").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id==" + ad + ")].reach").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id==" + ad + ")].clicks").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id==" + ad + ")].clickers").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id==" + ad + ")].todayImpressions").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id==" + plain + ")]").isEmpty());
    }

    @Test
    void coordinatesMustBeInKorea() throws Exception {
        mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brand\":\"x\",\"category\":\"뷰티\",\"benefit\":\"b\",\"area\":\"서울\","
                                + "\"sourceUrl\":\"https://example.com\",\"latitude\":51.5,\"longitude\":-0.1}"))
                .andExpect(status().isBadRequest());
    }
}
