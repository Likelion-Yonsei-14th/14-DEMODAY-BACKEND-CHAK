package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SneakyPhoneApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private AlertNotifier notifier;

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String auth(String name) {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
        return "Bearer " + jwt.createAccessToken(user);
    }

    /** 새 판을 만들고, 만든 지 secondsAgo초 지난 것처럼 만든 시각을 당긴다. */
    private String round(String authorization, int secondsAgo) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/sneaky-phone/rounds").header("X-Forwarded-For", ip());
        if (authorization != null) request.header("Authorization", authorization);
        String body = mockMvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameMs").value(30000))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("roundId").asString();
        jdbc.update("UPDATE minigame_rounds SET created_at = ? WHERE public_id = ?",
                Timestamp.from(Instant.now().minusSeconds(secondsAgo)), id);
        return id;
    }

    private MockHttpServletRequestBuilder submit(String roundId, String status, int base, double endedAt, double phoneMs, String authorization) {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/sneaky-phone/rounds/" + roundId + "/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"baseScore\":" + base + ",\"endedAt\":" + endedAt + ",\"totalPhoneMs\":" + phoneMs + "}");
        if (authorization != null) request.header("Authorization", authorization);
        return request;
    }

    @Test
    void survivingAddsTheBonusAndGuestsAreNotRanked() throws Exception {
        String id = round(null, 40);
        mockMvc.perform(submit(id, "survived", 6000, 30000, 25000, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseScore").value(6000))
                .andExpect(jsonPath("$.bonus").value(500))
                .andExpect(jsonPath("$.finalScore").value(6500))
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.rank").doesNotExist());
    }

    @Test
    void gettingCaughtHasNoBonus() throws Exception {
        String id = round(null, 20);
        mockMvc.perform(submit(id, "caught", 800, 12000.5, 4300.25, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonus").value(0))
                .andExpect(jsonPath("$.finalScore").value(800));
    }

    @Test
    void scoreAboveWhatThePhoneTimeAllowsIsRejectedButTheRoundStaysUsable() throws Exception {
        String id = round(null, 40);
        mockMvc.perform(submit(id, "survived", 9000, 30000, 25000, null))     // 25초로 낼 수 있는 최대는 7,000점대
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MINIGAME_RESULT"));
        mockMvc.perform(submit(id, "survived", 6000, 30000, 25000, null)).andExpect(status().isOk());
    }

    @Test
    void finishingFasterThanTheGameLastsIsRejected() throws Exception {
        String id = round(null, 3);                                   // 판을 만든 지 3초밖에 안 됐는데 30초를 버텼다고 함
        mockMvc.perform(submit(id, "survived", 100, 30000, 1000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MINIGAME_RESULT"));
    }

    @Test
    void inconsistentResultsAreRejected() throws Exception {
        String id = round(null, 60);
        mockMvc.perform(submit(id, "survived", 100, 10000, 1000, null)).andExpect(status().isBadRequest());   // 살아남았는데 30초 전에 끝남
        mockMvc.perform(submit(id, "caught", 100, 5000, 9000, null)).andExpect(status().isBadRequest());      // 폰을 든 시간이 게임 시간보다 김
        mockMvc.perform(submit(id, "won", 100, 5000, 1000, null)).andExpect(status().isBadRequest());         // 모르는 상태
        mockMvc.perform(submit(id, "caught", -1, 5000, 1000, null)).andExpect(status().isBadRequest());       // 음수 점수
    }

    @Test
    void aRoundCanOnlyBeSubmittedOnceAndOnlyThroughItsOwnGame() throws Exception {
        String id = round(null, 40);
        mockMvc.perform(submit(id, "caught", 100, 5000, 1000, null)).andExpect(status().isOk());
        mockMvc.perform(submit(id, "caught", 100, 5000, 1000, null)).andExpect(status().isConflict());
        mockMvc.perform(submit(UUID.randomUUID().toString(), "caught", 100, 5000, 1000, null)).andExpect(status().isNotFound());

        String guessGodRound = mapper.readTree(mockMvc.perform(post("/api/public/minigames/guess-god/rounds")
                .header("X-Forwarded-For", ip())).andReturn().getResponse().getContentAsString()).get("roundId").asString();
        mockMvc.perform(submit(guessGodRound, "caught", 100, 5000, 1000, null)).andExpect(status().isNotFound());
    }

    @Test
    void memberScoresAreRankedAndShownOnTheLeaderboard() throws Exception {
        String token = auth("서연");
        String id = round(token, 40);
        mockMvc.perform(submit(id, "survived", 7000, 30000, 28000, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.finalScore").value(7500))
                .andExpect(jsonPath("$.rank.bestScoreOverall").value(7500))
                .andExpect(jsonPath("$.rank.overall").value(1));

        mockMvc.perform(get("/api/public/minigames/sneaky-phone/leaderboard?period=daily").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.me == true)].nickname").value("서**"))
                .andExpect(jsonPath("$.entries[?(@.me == true)].score").value(7500))
                .andExpect(jsonPath("$.me.score").value(7500));
        mockMvc.perform(get("/api/public/minigames/sneaky-phone/leaderboard?period=weekly")).andExpect(status().isBadRequest());
    }

    @Test
    void guessGodAndSneakyPhoneBoardsDoNotMix() throws Exception {
        String token = auth("민호");
        mockMvc.perform(submit(round(token, 40), "survived", 7000, 30000, 28000, token)).andExpect(status().isOk());

        String board = mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=overall"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(board).doesNotContain("7500");
    }
}
