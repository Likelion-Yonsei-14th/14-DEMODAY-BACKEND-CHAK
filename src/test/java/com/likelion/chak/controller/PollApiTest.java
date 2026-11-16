package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PollApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String adminAuth() {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        return "Bearer " + jwt.createAccessToken(admin);
    }

    private JsonNode createPoll(String auth, Instant opensAt, Instant closesAt) throws Exception {
        String slug = "t-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"slug\":\"" + slug + "\",\"tag\":\"테스트\",\"question\":\"국어, 체감 난이도는?\","
                + "\"options\":[\"불수능\",\"평이했어\",\"물수능\"],\"opensAt\":\"" + opensAt + "\","
                + "\"closesAt\":" + (closesAt == null ? "null" : "\"" + closesAt + "\"") + "}";
        MvcResult res = mockMvc.perform(post("/api/admin/polls").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode findInList(String cookie, long pollId) throws Exception {
        var req = get("/api/public/polls");
        if (cookie != null) req = req.cookie(new Cookie("chak_guest_id", cookie));
        JsonNode list = mapper.readTree(mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode p : list.get("polls")) {
            if (p.get("id").asLong() == pollId) return p;
        }
        return null;
    }

    private MvcResult vote(long pollId, long optionId, String cookie, String region) throws Exception {
        var req = post("/api/public/polls/" + pollId + "/votes").header("X-Forwarded-For", ip())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionId\":" + optionId + ",\"geoCountry\":\"KR\",\"geoRegion\":\"" + region + "\"}");
        if (cookie != null) req = req.cookie(new Cookie("chak_guest_id", cookie));
        return mockMvc.perform(req).andReturn();
    }

    @Test
    void anonymousVisitorVotesOnceAndOnlyThenSeesTheResultsByRegion() throws Exception {
        Instant now = Instant.now();
        JsonNode poll = createPoll(adminAuth(), now.minus(1, ChronoUnit.HOURS), null);
        long id = poll.get("id").asLong();
        long hard = poll.get("options").get(0).get("id").asLong();
        long easy = poll.get("options").get(2).get("id").asLong();

        // 고르기 전에는 결과가 없다.
        JsonNode before = findInList(null, id);
        assertThat(before.get("status").asString()).isEqualTo("open");
        assertThat(before.get("results").isNull()).isTrue();

        // 처음 투표하면 손님 쿠키를 받고, 결과에 내 표가 들어 있다.
        MvcResult first = vote(id, hard, null, "11");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String cookie = first.getResponse().getCookie("chak_guest_id").getValue();
        JsonNode voted = mapper.readTree(first.getResponse().getContentAsString());
        assertThat(voted.get("myOptionId").asLong()).isEqualTo(hard);
        assertThat(voted.get("myRegion").asString()).isEqualTo("서울");
        assertThat(voted.get("results").get("total").asLong()).isEqualTo(1);
        assertThat(voted.get("results").get("counts").get(0).asLong()).isEqualTo(1);

        // 같은 사람이 다시 고르면 처음 표를 그대로 둔다.
        JsonNode again = mapper.readTree(vote(id, easy, cookie, "11").getResponse().getContentAsString());
        assertThat(again.get("myOptionId").asLong()).isEqualTo(hard);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM poll_votes WHERE poll_id = ?", Long.class, id)).isEqualTo(1);

        // 목록에서도 내가 고른 답과 결과가 보인다.
        JsonNode listed = findInList(cookie, id);
        assertThat(listed.get("myOptionId").asLong()).isEqualTo(hard);
        assertThat(listed.get("results").isNull()).isFalse();

        // IP는 저장하지 않는다.
        var row = jdbc.queryForMap("SELECT * FROM poll_votes WHERE poll_id = ?", id);
        assertThat(row.keySet()).noneMatch(c -> c.contains("IP"));
    }

    @Test
    void regionsAppearOnlyWithEnoughVotesAndUpcomingOrClosedPollsRejectVotes() throws Exception {
        String auth = adminAuth();
        Instant now = Instant.now();
        JsonNode poll = createPoll(auth, now.minus(1, ChronoUnit.HOURS), null);
        long id = poll.get("id").asLong();
        long hard = poll.get("options").get(0).get("id").asLong();
        for (int i = 0; i < 10; i++) {
            vote(id, hard, null, "41");
        }
        vote(id, hard, null, "26");
        Thread.sleep(5_100); // 결과 기억 시간이 지나도록
        JsonNode last = mapper.readTree(vote(id, hard, null, "26").getResponse().getContentAsString());
        JsonNode regions = last.get("results").get("regions");
        assertThat(last.get("results").get("total").asLong()).isEqualTo(12);
        assertThat(regions.size()).isEqualTo(1);
        assertThat(regions.get(0).get("region").asString()).isEqualTo("경기");
        assertThat(regions.get(0).get("total").asLong()).isEqualTo(10);

        JsonNode upcoming = createPoll(auth, now.plus(1, ChronoUnit.DAYS), null);
        assertThat(vote(upcoming.get("id").asLong(), upcoming.get("options").get(0).get("id").asLong(), null, "11")
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(findInList(null, upcoming.get("id").asLong()).get("status").asString()).isEqualTo("upcoming");

        // 마감하면 투표는 막히고 결과는 누구나 본다.
        mockMvc.perform(patch("/api/admin/polls/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"closesAt\":\"" + Instant.now().plusMillis(1) + "\"}")).andExpect(status().isOk());
        Thread.sleep(10);
        assertThat(vote(id, hard, null, "11").getResponse().getStatus()).isEqualTo(409);
        JsonNode closed = findInList(null, id);
        assertThat(closed.get("status").asString()).isEqualTo("closed");
        assertThat(closed.get("results").get("total").asLong()).isEqualTo(12);

        // 숨기면 목록에서 빠진다.
        mockMvc.perform(patch("/api/admin/polls/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}")).andExpect(status().isOk());
        assertThat(findInList(null, id)).isNull();
    }

    @Test
    void optionFromAnotherPollIsRejectedAndOnlyAdminsManagePolls() throws Exception {
        String auth = adminAuth();
        Instant now = Instant.now();
        JsonNode a = createPoll(auth, now.minus(1, ChronoUnit.HOURS), null);
        JsonNode b = createPoll(auth, now.minus(1, ChronoUnit.HOURS), null);
        assertThat(vote(a.get("id").asLong(), b.get("options").get(0).get("id").asLong(), null, "11").getResponse().getStatus())
                .isEqualTo(400);

        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        mockMvc.perform(get("/api/admin/polls").header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/polls").header("Authorization", auth)).andExpect(status().isOk());
        // 선택지가 하나뿐이면 만들 수 없다.
        mockMvc.perform(post("/api/admin/polls").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"x-one\",\"tag\":\"t\",\"question\":\"q\",\"options\":[\"a\"],\"opensAt\":\"" + now + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
