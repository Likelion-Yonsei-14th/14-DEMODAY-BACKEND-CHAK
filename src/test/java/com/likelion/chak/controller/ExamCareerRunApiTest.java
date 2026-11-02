package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExamCareerRunApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private static String started(String id) {
        return "{\"runId\":\"" + id + "\",\"track\":\"science\",\"finished\":false}";
    }

    private static String finished(String id, int attempt, double average, String enrolled, String title) {
        return "{\"runId\":\"" + id + "\",\"track\":\"science\",\"finished\":true,\"attempt\":" + attempt
                + ",\"finalAverage\":" + average + ",\"enrolled\":\"" + enrolled + "\",\"title\":\"" + title + "\"}";
    }

    private MockHttpServletRequestBuilder send(String body, String forwardedFor) {
        return post("/api/public/minigames/exam-career/runs").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor).content(body);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exam_career_runs", Long.class);
    }

    @Test
    void recordsAStartThenFillsInTheResultOnlyOnce() throws Exception {
        long before = count();
        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(started(id), ip())).andExpect(status().isCreated());
        var row = jdbc.queryForMap("SELECT * FROM exam_career_runs WHERE run_id = ?", id);
        assertThat(row.get("FINISHED")).isEqualTo(false);
        assertThat(row.keySet()).noneMatch(c -> c.contains("USER") || c.contains("NAME") || c.contains("IP") || c.contains("SEED"));

        mockMvc.perform(send(finished(id, 2, 2.4, "seoul-upper", "다시 일어선 도전자"), ip())).andExpect(status().isOk());
        mockMvc.perform(send(finished(id, 0, 8.0, "college", "버티기 달인"), ip())).andExpect(status().isOk());

        assertThat(count()).isEqualTo(before + 1);
        row = jdbc.queryForMap("SELECT * FROM exam_career_runs WHERE run_id = ?", id);
        assertThat(row.get("FINISHED")).isEqualTo(true);
        assertThat(((Number) row.get("ATTEMPT")).intValue()).isEqualTo(2);   // 처음 받은 결과가 남는다
        assertThat(row.get("ENROLLED")).isEqualTo("seoul-upper");
        assertThat(row.get("TITLE")).isEqualTo("다시 일어선 도전자");
    }

    @Test
    void aFinishedResultCanArriveWithoutAStartBeforeIt() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(finished(id, 0, 3.0, "none", "자유로운 영혼"), ip())).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT finished FROM exam_career_runs WHERE run_id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void rejectsRunsMadeOfUnknownValues() throws Exception {
        String id = UUID.randomUUID().toString();
        List<String> bad = List.of(
                started(id).replace("science", "arts"),
                finished(id, 1, 2.0, "harvard", "칭호"),
                finished(id, 1, 0.5, "sky", "칭호"),
                finished(id, 99, 2.0, "sky", "칭호"),
                finished(id, 1, 2.0, "sky", "<script>"),
                finished(id, 1, 2.0, "sky", ""),
                started("not-a-uuid"),
                "{\"runId\":\"" + id + "\",\"track\":\"science\",\"finished\":true}");
        long before = count();
        for (String body : bad) mockMvc.perform(send(body, ip())).andExpect(status().isBadRequest());
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void oneAddressCannotFloodIt() throws Exception {
        String flooder = ip();
        for (int i = 0; i < 120; i++) mockMvc.perform(send(started(UUID.randomUUID().toString()), flooder)).andExpect(status().isCreated());
        mockMvc.perform(send(started(UUID.randomUUID().toString()), flooder)).andExpect(status().isTooManyRequests());
    }

    @Test
    void adminSeesTheRunsAndOnlyAdminsCan() throws Exception {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영자", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        String auth = "Bearer " + jwt.createAccessToken(admin);
        JsonNode before = mapper.readTree(mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get("examCareer");

        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(started(id), ip())).andExpect(status().isCreated());
        mockMvc.perform(send(finished(id, 3, 3.5, "regional", "3수의 전설"), ip())).andExpect(status().isOk());
        mockMvc.perform(send(started(UUID.randomUUID().toString()), ip())).andExpect(status().isCreated());

        JsonNode after = mapper.readTree(mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get("examCareer");
        assertThat(after.get("started").asLong() - before.get("started").asLong()).isEqualTo(2);
        assertThat(after.get("startedToday").asLong() - before.get("startedToday").asLong()).isEqualTo(2);
        assertThat(after.get("finished").asLong() - before.get("finished").asLong()).isEqualTo(1);
        assertThat(count(after.get("attempts"), "3") - count(before.get("attempts"), "3")).isEqualTo(1);
        assertThat(count(after.get("enrolled"), "regional") - count(before.get("enrolled"), "regional")).isEqualTo(1);
        assertThat(count(after.get("titles"), "3수의 전설") - count(before.get("titles"), "3수의 전설")).isEqualTo(1);
        assertThat(after.get("days")).hasSize(14);
        assertThat(after.get("averageFinalGrade").isNumber()).isTrue();

        mockMvc.perform(get("/api/admin/minigames/stats")).andExpect(status().isUnauthorized());
    }

    private static long count(JsonNode rows, String key) {
        for (JsonNode row : rows) {
            if (row.get("key").asString().equals(key)) return row.get("count").asLong();
        }
        return 0;
    }
}
