package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMinigameStatsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UserAccount user(boolean admin) {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), admin ? "운영자" : "회원", null, null));
        user.updateAdmin(admin);
        return userRepository.save(user);
    }

    private JsonNode stats(UserAccount admin) throws Exception {
        String body = mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode game(JsonNode stats, String name) {
        for (JsonNode game : stats.get("games")) {
            if (game.get("game").asString().equals(name)) return game;
        }
        throw new AssertionError(name);
    }

    private void round(String game, Long userId, Integer score, Instant createdAt, Instant submittedAt) {
        jdbc.update("INSERT INTO minigame_rounds (public_id, game, answer_key, user_id, score, created_at, submitted_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), game, "", userId, score, Timestamp.from(createdAt),
                submittedAt == null ? null : Timestamp.from(submittedAt));
    }

    @Test
    void onlyAdminsCanReadMinigameStats() throws Exception {
        mockMvc.perform(get("/api/admin/minigames/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", "Bearer " + jwt.createAccessToken(user(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void countsStartedSubmittedAndMembersPerDay() throws Exception {
        UserAccount admin = user(true);
        UserAccount member = user(false);
        JsonNode before = game(stats(admin), "SNEAKY_PHONE");

        Instant now = Instant.now();
        round("SNEAKY_PHONE", member.getId(), 4000, now.minusSeconds(60), now);                    // 오늘, 회원
        round("SNEAKY_PHONE", null, 2000, now.minusSeconds(60), now);                              // 오늘, 비회원
        round("SNEAKY_PHONE", null, null, now.minusSeconds(30), null);                             // 오늘 시작만 하고 제출 안 함
        round("SNEAKY_PHONE", null, 1000, now.minus(3, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS));   // 3일 전
        round("SNEAKY_PHONE", null, 500, now.minus(10, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS));  // 10일 전(7일 밖, 14일 안)

        JsonNode after = game(stats(admin), "SNEAKY_PHONE");
        assertThat(after.get("startedToday").asLong() - before.get("startedToday").asLong()).isEqualTo(3);
        assertThat(after.get("submittedToday").asLong() - before.get("submittedToday").asLong()).isEqualTo(2);
        assertThat(after.get("submittedLast7Days").asLong() - before.get("submittedLast7Days").asLong()).isEqualTo(3);
        assertThat(after.get("submittedLast14Days").asLong() - before.get("submittedLast14Days").asLong()).isEqualTo(4);
        assertThat(after.get("rankedMembers").asLong() - before.get("rankedMembers").asLong()).isEqualTo(1);
        assertThat(after.get("bestMemberScore").asInt()).isGreaterThanOrEqualTo(4000);
        assertThat(after.get("days")).hasSize(14);
        JsonNode today = after.get("days").get(13);
        JsonNode todayBefore = before.get("days").get(13);
        assertThat(today.get("members").asLong() - todayBefore.get("members").asLong()).isEqualTo(1);
        assertThat(today.get("guests").asLong() - todayBefore.get("guests").asLong()).isEqualTo(1);
    }

    @Test
    void includesEveryGameAndTheStudyTypeSummary() throws Exception {
        JsonNode result = stats(user(true));
        assertThat(result.get("games")).hasSize(2);
        assertThat(game(result, "GUESS_GOD").get("days")).hasSize(14);
        JsonNode study = result.get("studyType");
        assertThat(study.get("distribution")).hasSize(8);
        assertThat(study.get("total").asLong()).isGreaterThanOrEqualTo(study.get("members").asLong());
    }
}
