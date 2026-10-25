package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "chak.load-test.provision-key=01234567890123456789012345678901")
@ActiveProfiles("loadtest")
@AutoConfigureMockMvc
class LoadTestAccountControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void requiresProvisionKey() throws Exception {
        mvc.perform(post("/api/load-test/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-001\",\"count\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/load-test/actual?date=2026-10-25"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readsDailyActualsWithProvisionKey() throws Exception {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString();
        mvc.perform(get("/api/load-test/actual")
                        .param("date", today)
                        .header("X-Chak-Load-Key", "01234567890123456789012345678901"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.date").value(today))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.members").isNumber())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.guests").isNumber());
    }

    @Test
    void provisionsRealJwtAccountsThatCanRefresh() throws Exception {
        String body = mvc.perform(post("/api/load-test/accounts")
                        .header("X-Chak-Load-Key", "01234567890123456789012345678901")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"load-002\",\"count\":2}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var accounts = json.readTree(body);
        assertThat(accounts.size()).isEqualTo(2);
        String access = accounts.get(0).get("accessToken").asText();
        String refresh = accounts.get(0).get("refreshToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        String rotated = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(rotated).get("refreshToken").asText()).isNotEqualTo(refresh);
    }
}
