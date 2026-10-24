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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MinigameApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private AlertNotifier notifier;

    private UserAccount member(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private String auth(UserAccount user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String newRound(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/guess-god/rounds").header("X-Forwarded-For", ip());
        if (authorization != null) request.header("Authorization", authorization);
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionCount").value(10))
                .andExpect(jsonPath("$.choiceCount").value(5))
                .andExpect(jsonPath("$.answerKey").doesNotExist())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("roundId").asString();
    }

    private List<Integer> keyOf(String roundId) {
        String key = jdbc.queryForObject("SELECT answer_key FROM minigame_rounds WHERE public_id = ?", String.class, roundId);
        List<Integer> answers = new ArrayList<>();
        for (char c : key.toCharArray()) answers.add(c - '0');
        return answers;
    }

    /** 정답 중 correct개만 맞히고 나머지는 틀린 번호를 낸다. */
    private List<Integer> answersWithScore(String roundId, int correct) {
        List<Integer> key = keyOf(roundId);
        List<Integer> answers = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) {
            answers.add(i < correct ? key.get(i) : key.get(i) % 5 + 1);
        }
        return answers;
    }

    private MockHttpServletRequestBuilder submit(String roundId, List<Integer> answers, String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/public/minigames/guess-god/rounds/" + roundId + "/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("answers", answers)));
        if (authorization != null) request.header("Authorization", authorization);
        return request;
    }

    @Test
    void guestIsGradedByTheServerButNotRanked() throws Exception {
        String round = newRound(null);

        mockMvc.perform(submit(round, answersWithScore(round, 7), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(7))
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.answerKey.length()").value(10));
    }

    @Test
    void roundCanOnlyBeSubmittedOnce() throws Exception {
        String round = newRound(null);
        mockMvc.perform(submit(round, answersWithScore(round, 10), null)).andExpect(status().isOk());

        mockMvc.perform(submit(round, answersWithScore(round, 10), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MINIGAME_ROUND_ALREADY_SUBMITTED"));
    }

    @Test
    void unknownRoundAndBadAnswersAreRejected() throws Exception {
        mockMvc.perform(submit(UUID.randomUUID().toString(), answersWithScore(newRound(null), 0), null))
                .andExpect(status().isNotFound());

        String round = newRound(null);
        List<Integer> tooShort = answersWithScore(round, 3).subList(0, 9);
        mockMvc.perform(submit(round, tooShort, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MINIGAME_ANSWERS"));
        List<Integer> outOfRange = new ArrayList<>(answersWithScore(round, 3));
        outOfRange.set(0, 6);
        mockMvc.perform(submit(round, outOfRange, null)).andExpect(status().isBadRequest());
        // 잘못된 제출은 판을 쓰지 않으므로 올바르게 다시 낼 수 있다.
        mockMvc.perform(submit(round, answersWithScore(round, 3), null)).andExpect(status().isOk());
    }

    @Test
    void unansweredQuestionsCountAsWrong() throws Exception {
        String round = newRound(null);
        List<Integer> answers = new ArrayList<>(answersWithScore(round, 10));
        answers.set(0, null);
        answers.set(1, null);

        mockMvc.perform(submit(round, answers, null)).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(8));
    }

    @Test
    void memberScoreIsRankedAndShownWithNicknameOnTheLeaderboard() throws Exception {
        UserAccount me = member("홍길동");
        String token = auth(me);
        String round = newRound(token);

        mockMvc.perform(submit(round, answersWithScore(round, 10), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.rank.overall").value(1))
                .andExpect(jsonPath("$.rank.bestScoreToday").value(10));

        // 닉네임을 정하기 전에는 이름 첫 글자만 보인다.
        mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=daily").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.me == true)].nickname").value("홍**"))
                .andExpect(jsonPath("$.me.score").value(10))
                .andExpect(jsonPath("$.distribution.length()").value(11));

        mockMvc.perform(put("/api/minigames/me/nickname").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"  찍신왕  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("찍신왕"));

        mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.nickname == '찍신왕')].score").value(10))
                .andExpect(jsonPath("$.me").doesNotExist());
    }

    @Test
    void leaderboardUsesBestScoreAndLetsTiesShareARank() throws Exception {
        UserAccount a = member("가나다");
        UserAccount b = member("라마바");
        int[][] plays = {{0, 9}, {0, 4}, {1, 9}};   // a: 9와 4 → 최고 9, b: 9
        for (int[] play : plays) {
            UserAccount who = play[0] == 0 ? a : b;
            String round = newRound(auth(who));
            mockMvc.perform(submit(round, answersWithScore(round, play[1]), auth(who))).andExpect(status().isOk());
        }

        JsonNode board = mapper.readTree(mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=overall"))
                .andReturn().getResponse().getContentAsString());
        int rankA = -1, rankB = -1;
        for (JsonNode entry : board.get("entries")) {
            if (entry.get("nickname").asString().equals("가**")) rankA = entry.get("rank").asInt();
            if (entry.get("nickname").asString().equals("라**")) rankB = entry.get("rank").asInt();
        }
        assertThat(rankA).isPositive();
        assertThat(rankA).isEqualTo(rankB);
    }

    @Test
    void withdrawnMembersDisappearFromTheLeaderboard() throws Exception {
        UserAccount gone = member("사라짐");
        String round = newRound(auth(gone));
        mockMvc.perform(submit(round, answersWithScore(round, 10), auth(gone))).andExpect(status().isOk());
        gone.anonymize(java.time.Instant.now());
        userRepository.save(gone);

        String body = mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=overall"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("사**").doesNotContain("탈퇴");
    }

    @Test
    void invalidPeriodIsRejected() throws Exception {
        mockMvc.perform(get("/api/public/minigames/guess-god/leaderboard?period=weekly")).andExpect(status().isBadRequest());
    }

    @Test
    void creatingRoundsIsRateLimitedPerClient() throws Exception {
        String client = ip();
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(post("/api/public/minigames/guess-god/rounds").header("X-Forwarded-For", client))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/public/minigames/guess-god/rounds").header("X-Forwarded-For", client))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void nicknameIsMembersOnlyAndValidated() throws Exception {
        mockMvc.perform(put("/api/minigames/me/nickname").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"손님\"}"))
                .andExpect(status().isUnauthorized());

        String token = auth(member("김철수"));
        for (String bad : new String[]{"a", "열두자를넘기는아주긴닉네임입니다", "<b>x</b>", "   "}) {
            mockMvc.perform(put("/api/minigames/me/nickname").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/minigames/me/nickname").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").doesNotExist())
                .andExpect(jsonPath("$.defaultNickname").value("김**"));
    }

    @Test
    void badWordNicknamesAreRejectedAndTheOldNicknameStays() throws Exception {
        String token = auth(member("박민수"));
        mockMvc.perform(put("/api/minigames/me/nickname").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"착한사람\"}"))
                .andExpect(status().isOk());

        for (String bad : new String[]{"시발", "시1발", "운영자", "admin"}) {
            mockMvc.perform(put("/api/minigames/me/nickname").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NICKNAME_NOT_ALLOWED"));
        }
        mockMvc.perform(get("/api/minigames/me/nickname").header("Authorization", token))
                .andExpect(jsonPath("$.nickname").value("착한사람"));
    }

    @Test
    void studyTypeStatsCountGuestsAndKeepOneRowPerMember() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM study_type_results", Long.class);
        UserAccount me = member("유형");
        String token = auth(me);

        mockMvc.perform(post("/api/public/minigames/study-type/results").header("X-Forwarded-For", ip())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"PSA\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.myCode").doesNotExist());
        for (String code : new String[]{"LFN", "PSN"}) {                          // 회원이 다시 해도 한 줄
            mockMvc.perform(post("/api/public/minigames/study-type/results").header("X-Forwarded-For", ip())
                            .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"" + code + "\"}"))
                    .andExpect(status().isCreated());
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_type_results", Long.class)).isEqualTo(before + 2);
        mockMvc.perform(get("/api/public/minigames/study-type/stats").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myCode").value("PSN"))
                .andExpect(jsonPath("$.distribution.length()").value(8))
                .andExpect(jsonPath("$.total").value(before + 2));
    }

    @Test
    void studyTypeRejectsUnknownCodes() throws Exception {
        for (String bad : new String[]{"XXX", "PS", "psa", "PSAA", ""}) {
            mockMvc.perform(post("/api/public/minigames/study-type/results").header("X-Forwarded-For", ip())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
