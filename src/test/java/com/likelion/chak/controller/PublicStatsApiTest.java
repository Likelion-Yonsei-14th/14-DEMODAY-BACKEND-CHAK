package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicStatsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void visitorSeriesIsPublicAndRunsFromTheFirstDayToYesterday() throws Exception {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        int days = (int) ChronoUnit.DAYS.between(PublicStatsController.FIRST_DAY, yesterday) + 1;

        mockMvc.perform(get("/api/public/stats/visitors"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=600")))
                .andExpect(jsonPath("$.from").value("2026-10-28"))
                .andExpect(jsonPath("$.to").value(yesterday.toString()))
                .andExpect(jsonPath("$.days", hasSize(days)))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-28"))
                .andExpect(jsonPath("$.days[0].visitors").isNumber());
    }

    @Test
    void onlyDatesAndCountsAreExposed() throws Exception {
        mockMvc.perform(get("/api/public/stats/visitors"))
                .andExpect(jsonPath("$.days[0].memberVisitors").doesNotExist())
                .andExpect(jsonPath("$.days[0].pageViews").doesNotExist())
                .andExpect(jsonPath("$.totalUsers").doesNotExist());
    }
}
