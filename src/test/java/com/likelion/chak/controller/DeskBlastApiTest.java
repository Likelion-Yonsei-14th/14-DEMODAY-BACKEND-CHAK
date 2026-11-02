package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeskBlastApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private AlertNotifier notifier;

    /** 순위는 모든 회원이 함께 쓰는 표라서, 앞선 테스트가 남긴 판이 섞이지 않게 비운다. */
    @BeforeEach
    void clearRounds() {
        jdbc.update("DELETE FROM minigame_rounds WHERE game = 'DESK_BLAST'");
    }

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String auth(String name) {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
        return "Bearer " + jwt.createAccessToken(user);
    }

    /** 새 판을 만들고, 만든 지 secondsAgo초 지난 것처럼 만든 시각을 당긴다. */
    private String round(String authorization, int secondsAgo) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/desk-blast/rounds").header("X-Forwarded-For", ip());
        if (authorization != null) request.header("Authorization", authorization);
        String body = mockMvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("roundId").asString();
        jdbc.update("UPDATE minigame_rounds SET created_at = ? WHERE public_id = ?",
                Timestamp.from(Instant.now().minusSeconds(secondsAgo)), id);
        return id;
    }

    private MockHttpServletRequestBuilder submit(String roundId, int score, int lines, int bestCombo, int placements, long playMs, String authorization) {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/desk-blast/rounds/" + roundId + "/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":" + score + ",\"lines\":" + lines + ",\"bestCombo\":" + bestCombo
                        + ",\"placements\":" + placements + ",\"playMs\":" + playMs + "}");
        if (authorization != null) request.header("Authorization", authorization);
        return request;
    }

    @Test
    void aPlausibleGameIsAcceptedAndGuestsAreNotRanked() throws Exception {
        String id = round(null, 120);
        mockMvc.perform(submit(id, 3000, 20, 5, 50, 90_000, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(3000))
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.rank").doesNotExist());
    }

    @Test
    void memberScoresAreRankedAndShownOnTheLeaderboard() throws Exception {
        String token = auth("서연");
        String id = round(token, 120);
        mockMvc.perform(submit(id, 4200, 24, 6, 60, 100_000, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.rank.bestScoreOverall").value(4200))
                .andExpect(jsonPath("$.rank.overall").value(1));

        mockMvc.perform(get("/api/public/minigames/desk-blast/leaderboard?period=daily").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("daily"))
                .andExpect(jsonPath("$.entries[?(@.me == true)].nickname").value("서**"))
                .andExpect(jsonPath("$.entries[?(@.me == true)].score").value(4200))
                .andExpect(jsonPath("$.me.score").value(4200));
        mockMvc.perform(get("/api/public/minigames/desk-blast/leaderboard?period=overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("overall"));
        mockMvc.perform(get("/api/public/minigames/desk-blast/leaderboard?period=weekly")).andExpect(status().isBadRequest());
    }

    @Test
    void aMemberKeepsOnlyTheBestScoreOnTheBoard() throws Exception {
        String token = auth("지후");
        mockMvc.perform(submit(round(token, 120), 5000, 28, 6, 70, 100_000, token)).andExpect(status().isOk());
        mockMvc.perform(submit(round(token, 120), 1200, 8, 2, 40, 60_000, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank.bestScoreOverall").value(5000));
    }

    @Test
    void scoreAboveWhatThePlacementsAllowIsRejectedButTheRoundStaysUsable() throws Exception {
        String id = round(null, 120);
        // 놓기 50번, 줄 20개, 최고 콤보 5로 낼 수 있는 최대는 12,450점
        mockMvc.perform(submit(id, 99_999, 20, 5, 50, 90_000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MINIGAME_RESULT"));
        mockMvc.perform(submit(id, 3000, 20, 5, 50, 90_000, null)).andExpect(status().isOk());
    }

    @Test
    void numbersThatCannotBelongToTheSameGameAreRejected() throws Exception {
        String id = round(null, 300);
        // 놓기 한 번에 줄이 상한보다 많음
        mockMvc.perform(submit(id, 500, 3, 1, 1, 5_000, null)).andExpect(status().isBadRequest());
        // 콤보가 지운 줄 수보다 큼
        mockMvc.perform(submit(id, 500, 2, 3, 40, 60_000, null)).andExpect(status().isBadRequest());
        // 콤보가 놓은 횟수보다 큼
        mockMvc.perform(submit(id, 500, 8, 5, 4, 60_000, null)).andExpect(status().isBadRequest());
        // 놓은 횟수보다 점수가 낮음(놓을 때마다 칸 점수를 받는다)
        mockMvc.perform(submit(id, 10, 0, 0, 40, 60_000, null)).andExpect(status().isBadRequest());
    }

    @Test
    void invalidFieldsAreRejectedBeforeAnyChecking() throws Exception {
        String id = round(null, 120);
        mockMvc.perform(submit(id, -1, 0, 0, 10, 60_000, null)).andExpect(status().isBadRequest());
        mockMvc.perform(submit(id, 100, 0, 0, 0, 60_000, null)).andExpect(status().isBadRequest());
        mockMvc.perform(submit(id, 100, 0, 0, 10, -5, null)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/public/minigames/desk-blast/rounds/" + id + "/submit")
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":100}")).andExpect(status().isBadRequest());
    }

    @Test
    void placingBlocksFasterThanAPersonCanIsRejected() throws Exception {
        String id = round(null, 300);
        // 50번을 3초에: 한 번에 0.06초
        mockMvc.perform(submit(id, 1000, 5, 2, 50, 3_000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MINIGAME_RESULT"));
    }

    @Test
    void playingLongerThanTheRoundHasExistedIsRejected() throws Exception {
        String id = round(null, 5);                                   // 판을 만든 지 5초밖에 안 됐는데 10분을 플레이했다고 함
        mockMvc.perform(submit(id, 3000, 20, 5, 50, 600_000, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MINIGAME_RESULT"));
    }

    @Test
    void aRoundCanOnlyBeSubmittedOnceAndOnlyThroughItsOwnGame() throws Exception {
        String id = round(null, 120);
        mockMvc.perform(submit(id, 3000, 20, 5, 50, 90_000, null)).andExpect(status().isOk());
        mockMvc.perform(submit(id, 3000, 20, 5, 50, 90_000, null)).andExpect(status().isConflict());
        mockMvc.perform(submit(UUID.randomUUID().toString(), 3000, 20, 5, 50, 90_000, null)).andExpect(status().isNotFound());

        String sneakyRound = mapper.readTree(mockMvc.perform(post("/api/public/minigames/sneaky-phone/rounds")
                .header("X-Forwarded-For", ip())).andReturn().getResponse().getContentAsString()).get("roundId").asString();
        mockMvc.perform(submit(sneakyRound, 3000, 20, 5, 50, 90_000, null)).andExpect(status().isNotFound());
    }

    @Test
    void aRoundThatIsTooOldIsNotFound() throws Exception {
        String id = round(null, 25 * 3600);

        mockMvc.perform(submit(id, 3000, 20, 5, 50, 90_000, null)).andExpect(status().isNotFound());
    }

    @Test
    void theBoardsOfDifferentGamesDoNotMix() throws Exception {
        String token = auth("민호");
        mockMvc.perform(submit(round(token, 120), 4321, 24, 6, 60, 100_000, token)).andExpect(status().isOk());

        for (String game : new String[] {"guess-god", "sneaky-phone"}) {
            String board = mockMvc.perform(get("/api/public/minigames/" + game + "/leaderboard?period=overall"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(board).doesNotContain("4321");
        }
    }

    @Test
    void anyoneCanStartRoundsWithoutLoggingIn() throws Exception {
        mockMvc.perform(post("/api/public/minigames/desk-blast/rounds").header("X-Forwarded-For", ip()))
                .andExpect(status().isCreated());
    }
}
