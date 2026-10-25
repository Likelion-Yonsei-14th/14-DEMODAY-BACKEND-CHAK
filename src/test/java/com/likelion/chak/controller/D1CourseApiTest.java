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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class D1CourseApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private static String json(String id, String region, String district, String area, String activities, String companion, String budget, boolean shared) {
        return "{\"responseId\":\"" + id + "\",\"startRegion\":\"" + region + "\",\"startDistrict\":"
                + (district == null ? "null" : "\"" + district + "\"") + ",\"area\":\"" + area + "\",\"activities\":" + activities
                + ",\"companion\":\"" + companion + "\",\"budget\":\"" + budget + "\",\"shared\":" + shared + "}";
    }

    private static String valid(String id, boolean shared) {
        return json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "30kTo100k", shared);
    }

    private MockHttpServletRequestBuilder send(String body, String forwardedFor) {
        return post("/api/public/minigames/d1-course/responses").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor).content(body);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM d1_course_responses", Long.class);
    }

    @Test
    void storesAnAnswerWithoutAnyAccountAndEvenWhenALoginTokenIsSent() throws Exception {
        long before = count();
        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(valid(id, false), ip())).andExpect(status().isCreated()).andExpect(jsonPath("$.received").value(true));

        assertThat(count()).isEqualTo(before + 1);
        var row = jdbc.queryForMap("SELECT * FROM d1_course_responses WHERE response_id = ?", id);
        assertThat(row.get("START_REGION")).isEqualTo("seoul");
        assertThat(row.get("START_DISTRICT")).isEqualTo("마포구");
        assertThat(row.get("ACTIVITY1")).isEqualTo("dye");
        assertThat(row.get("ACTIVITY3")).isEqualTo("food");
        assertThat(row.keySet()).noneMatch(c -> c.contains("USER") || c.contains("GUEST") || c.contains("IP"));

        // 토큰을 같이 보내도 계정과 연결되지 않는다(저장하는 열 자체가 없다).
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        mockMvc.perform(send(valid(UUID.randomUUID().toString(), false), ip()).header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isCreated());
    }

    @Test
    void sendingTheSameAnswerAgainOnlyMarksItSharedAndKeepsTheOriginalChoices() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(valid(id, false), ip())).andExpect(status().isCreated());
        long before = count();

        String tampered = json(id.toUpperCase(), "busan", "해운대구", "gangnam", "[\"karaoke\",\"escape\",\"license\"]", "alone", "over100k", true);
        mockMvc.perform(send(tampered, ip())).andExpect(status().isOk());

        assertThat(count()).isEqualTo(before);                                // 새 줄이 생기지 않는다
        var row = jdbc.queryForMap("SELECT * FROM d1_course_responses WHERE response_id = ?", id);
        assertThat(row.get("SHARED")).isEqualTo(true);
        assertThat(row.get("START_REGION")).isEqualTo("seoul");               // 답 내용은 처음 그대로
        assertThat(row.get("AREA")).isEqualTo("hongdae");

        mockMvc.perform(send(valid(id, false), ip())).andExpect(status().isOk());   // 공유 표시는 내려가지 않는다
        assertThat(jdbc.queryForObject("SELECT shared FROM d1_course_responses WHERE response_id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void rejectsAnswersThatAreNotMadeOfKnownChoices() throws Exception {
        String id = UUID.randomUUID().toString();
        List<String> bad = List.of(
                json(id, "mars", "마포구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false),            // 모르는 시·도
                json(id, "seoul", "마포구", "moon", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false),             // 모르는 장소
                json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"lens\"]", "small", "under30k", false),                  // 두 개뿐
                json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"dye\",\"food\"]", "small", "under30k", false),          // 중복
                json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"lens\",\"hack\"]", "small", "under30k", false),         // 모르는 활동
                json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "everyone", "under30k", false),      // 모르는 동행
                json(id, "seoul", "마포구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "free", false),             // 모르는 예산
                json(id, "sejong", "아무구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false),        // 세종에는 구가 없다
                json("not-a-uuid", "seoul", "마포구", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false),
                json(id, "seoul", "<script>", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false));
        long before = count();
        for (String body : bad) mockMvc.perform(send(body, ip())).andExpect(status().isBadRequest());
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void sejongHasNoDistrictAndAnsweringWithoutOneIsFine() throws Exception {
        mockMvc.perform(send(json(UUID.randomUUID().toString(), "sejong", null, "local", "[\"photo\",\"clothes\",\"themepark\"]", "family", "over100k", false), ip()))
                .andExpect(status().isCreated());
    }

    @Test
    void oneAddressCannotFloodTheSurvey() throws Exception {
        String flooder = ip();
        for (int i = 0; i < 60; i++) mockMvc.perform(send(valid(UUID.randomUUID().toString(), false), flooder)).andExpect(status().isCreated());
        mockMvc.perform(send(valid(UUID.randomUUID().toString(), false), flooder)).andExpect(status().isTooManyRequests());
        mockMvc.perform(send(valid(UUID.randomUUID().toString(), false), ip())).andExpect(status().isCreated());
    }

    @Test
    void adminSeesTheDistributionsAndOnlyAdminsCan() throws Exception {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영자", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        String auth = "Bearer " + jwt.createAccessToken(admin);
        JsonNode before = mapper.readTree(mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get("d1Course");

        String id = UUID.randomUUID().toString();
        mockMvc.perform(send(json(id, "gyeonggi", "고양시", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", false), ip())).andExpect(status().isCreated());
        mockMvc.perform(send(json(id, "gyeonggi", "고양시", "hongdae", "[\"dye\",\"lens\",\"food\"]", "small", "under30k", true), ip())).andExpect(status().isOk());

        JsonNode after = mapper.readTree(mockMvc.perform(get("/api/admin/minigames/stats").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get("d1Course");
        assertThat(after.get("total").asLong() - before.get("total").asLong()).isEqualTo(1);
        assertThat(after.get("today").asLong() - before.get("today").asLong()).isEqualTo(1);
        assertThat(after.get("shared").asLong() - before.get("shared").asLong()).isEqualTo(1);
        assertThat(count(after.get("activities"), "dye") - count(before.get("activities"), "dye")).isEqualTo(1);
        assertThat(count(after.get("regions"), "gyeonggi") - count(before.get("regions"), "gyeonggi")).isEqualTo(1);
        assertThat(count(after.get("areas"), "hongdae")).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/admin/minigames/stats")).andExpect(status().isUnauthorized());
    }

    private static long count(JsonNode rows, String key) {
        for (JsonNode row : rows) {
            if (row.get("key").asString().equals(key)) return row.get("count").asLong();
        }
        return 0;
    }
}
