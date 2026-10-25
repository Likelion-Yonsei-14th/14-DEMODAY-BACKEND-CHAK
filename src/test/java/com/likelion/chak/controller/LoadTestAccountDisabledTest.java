package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoadTestAccountDisabledTest {

    @Autowired private MockMvc mvc;

    @Test
    void provisioningEndpointIsAbsentWithoutLoadtestProfile() throws Exception {
        mvc.perform(post("/api/load-test/accounts")
                .header("X-Chak-Load-Key", "01234567890123456789012345678901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-003\",\"count\":2}"))
                .andExpect(status().isNotFound());
    }
}
