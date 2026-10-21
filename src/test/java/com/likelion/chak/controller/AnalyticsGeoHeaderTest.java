package com.likelion.chak.controller;

import com.likelion.chak.repository.TrafficEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsGeoHeaderTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TrafficEventRepository trafficEventRepository;

    private String record(String... headers) throws Exception {
        String eventId = UUID.randomUUID().toString();
        var request = post("/api/public/analytics/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + eventId + "\",\"eventType\":\"PAGE_VIEW\",\"sessionId\":\"s-1\",\"path\":\"/home\"}");
        for (int i = 0; i < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
        mockMvc.perform(request).andExpect(status().isCreated());
        return eventId;
    }

    @Test
    void middlewareHeadersAreStoredAsCountryAndRegion() throws Exception {
        String id = record("X-Chak-Geo-Country", "KR", "X-Chak-Geo-Region", "11");
        var event = trafficEventRepository.findByEventId(id).orElseThrow();
        assertThat(event.getCountry()).isEqualTo("KR");
        assertThat(event.getRegion()).isEqualTo("11");
    }

    @Test
    void middlewareHeadersWinOverHostingHeadersAndBlankFallsBack() throws Exception {
        var both = trafficEventRepository.findByEventId(record(
                "X-Chak-Geo-Country", "KR", "X-Chak-Geo-Region", "26",
                "X-Vercel-IP-Country", "US", "X-Vercel-IP-Country-Region", "CA")).orElseThrow();
        assertThat(both.getRegion()).isEqualTo("26");

        var blank = trafficEventRepository.findByEventId(record(
                "X-Chak-Geo-Country", "", "X-Chak-Geo-Region", "",
                "X-Vercel-IP-Country", "KR", "X-Vercel-IP-Country-Region", "27")).orElseThrow();
        assertThat(blank.getCountry()).isEqualTo("KR");
        assertThat(blank.getRegion()).isEqualTo("27");
    }

    @Test
    void noHeadersLeavesLocationUnknown() throws Exception {
        var event = trafficEventRepository.findByEventId(record()).orElseThrow();
        assertThat(event.getCountry()).isNull();
        assertThat(event.getRegion()).isNull();
    }
}
