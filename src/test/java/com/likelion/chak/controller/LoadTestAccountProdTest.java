package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chak.secrets.id=",
        "chak.db-secret-id=",
        "chak.load-test.provision-key="
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class LoadTestAccountProdTest {

    @Autowired private MockMvc mvc;

    @Test
    void refusesProvisioningWithoutConfiguredKey() throws Exception {
        mvc.perform(post("/api/load-test/accounts")
                .header("X-Chak-Load-Key", "01234567890123456789012345678901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"load-prod\",\"count\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/load-test/actual?date=2026-10-25")
                .header("X-Chak-Load-Key", "01234567890123456789012345678901"))
                .andExpect(status().isForbidden());
    }
}
