package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 본문 크기·JSON 구조 상한이 실제 요청 경로에 걸려 있는지 본다(F1). */
@SpringBootTest
@AutoConfigureMockMvc
class RequestLimitsApiTest {

    @Autowired private MockMvc mockMvc;

    private static String event(String metadata) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PAGE_VIEW\",\"sessionId\":\"s\",\"path\":\"/x\""
                + ",\"metadata\":" + metadata + "}";
    }

    @Test
    void anOversizedPublicBodyIsRefusedWith413() throws Exception {
        String huge = "[" + "{},".repeat(300_000) + "{}]";   // 약 900KB
        mockMvc.perform(post("/api/public/analytics/events").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.1").content(event(huge)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void deeplyNestedJsonIsRefusedBeforeBuildingTheTree() throws Exception {
        String deep = "[".repeat(200) + "]".repeat(200);
        mockMvc.perform(post("/api/public/analytics/events").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.2").content(event(deep)))
                .andExpect(status().isBadRequest());
    }
}
