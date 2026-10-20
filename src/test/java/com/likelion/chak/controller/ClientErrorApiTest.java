package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertMessage;
import com.likelion.chak.alert.AlertNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientErrorApiTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AlertNotifier notifier;

    @BeforeEach
    void resetNotifier() {
        reset(notifier);
    }

    private MockHttpServletRequestBuilder report(String json, String ip) {
        return post("/api/public/client-errors")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) KAKAOTALK 10.0")
                .content(json);
    }

    private static String uniqueIp() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    @Test
    void acceptsAReportWithoutLoginAndAlertsWithSanitizedContent() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(report("""
                        {"kind":"api_error","message":"boom %s kim@yonsei.ac.kr","route":"/desk/abc?token=secret",
                         "apiPath":"/api/public/desks/abc?x=1","status":500,"code":"INTERNAL_SERVER_ERROR",
                         "buildId":"1b72ebd","reportId":"r-1"}""".formatted(unique), uniqueIp()))
                .andExpect(status().isAccepted());

        var captor = forClass(AlertMessage.class);
        verify(notifier, timeout(2000)).notify(captor.capture(), anyInt());
        AlertMessage sent = captor.getValue();
        assertThat(sent.title()).isEqualTo("API 오류 500");
        assertThat(sent.severity()).isEqualTo(AlertMessage.Severity.ERROR);
        assertThat(sent.fields().get("화면")).isEqualTo("/desk/abc");
        assertThat(sent.fields().get("브라우저")).isEqualTo("iOS · 카카오톡 인앱");
        assertThat(sent.description()).doesNotContain("yonsei").contains("[이메일]");
    }

    @Test
    void rejectsInvalidReports() throws Exception {
        mockMvc.perform(report("{\"kind\":\"hack\",\"message\":\"x\"}", uniqueIp()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(report("{\"kind\":\"js_error\"}", uniqueIp())).andExpect(status().isBadRequest());
        mockMvc.perform(report("{\"kind\":\"js_error\",\"message\":\"" + "a".repeat(401) + "\"}", uniqueIp()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(report("{\"kind\":\"js_error\",\"message\":\"x\",\"status\":99999}", uniqueIp()))
                .andExpect(status().isBadRequest());
        verify(notifier, never()).notify(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void oneClientCannotFloodTheEndpoint() throws Exception {
        String ip = uniqueIp();
        int accepted = 0;
        int limited = 0;
        for (int i = 0; i < 15; i++) {
            var result = mockMvc.perform(report(
                    "{\"kind\":\"js_error\",\"message\":\"flood " + i + "\"}", ip)).andReturn();
            if (result.getResponse().getStatus() == 202) {
                accepted++;
            } else if (result.getResponse().getStatus() == 429) {
                limited++;
            }
        }
        assertThat(accepted).isEqualTo(10);
        assertThat(limited).isEqualTo(5);
    }
}
