package com.likelion.chak.controller;

import com.likelion.chak.repository.GuestIdentityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class GuestCreationLimitApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestIdentityRepository guestRepository;

    private MockHttpServletResponse visit(String ip, String guestCookie) throws Exception {
        var request = post("/api/public/analytics/events").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip + ", 76.76.21.21")
                .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PAGE_VIEW\",\"sessionId\":\"s\",\"path\":\"/x\"}");
        if (guestCookie != null) request.cookie(new jakarta.servlet.http.Cookie("chak_guest_id", guestCookie));
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void floodOfCookielessVisitsStopsCreatingGuestsButAKnownGuestKeepsWorking() throws Exception {
        String ip = "203.0." + (Math.abs(UUID.randomUUID().hashCode()) % 250) + "." + (Math.abs(UUID.randomUUID().hashCode()) % 250);
        long before = guestRepository.count();
        String firstCookie = null;
        int created = 0;
        int rejected = 0;
        for (int i = 0; i < 45; i++) {
            var response = visit(ip, null);
            if (response.getStatus() == 201) {
                created++;
                if (firstCookie == null) {
                    String set = response.getHeader("Set-Cookie");
                    firstCookie = set.substring(set.indexOf('=') + 1, set.indexOf(';'));
                }
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                rejected++;
            }
        }
        assertThat(created).isEqualTo(30);
        assertThat(rejected).isEqualTo(15);
        assertThat(guestRepository.count() - before).isEqualTo(30);

        // 이미 쿠키가 있는 손님은 제한 대상이 아니다(새로 만들지 않으므로).
        assertThat(visit(ip, firstCookie).getStatus()).isEqualTo(201);
    }
}
