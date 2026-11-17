package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.AdSwitchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdAdminApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private AdSwitchService adSwitchService;

    @AfterEach
    void turnAdsOff() {
        adSwitchService.set(false, null);
    }

    private String auth(boolean admin) {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        user.updateAdmin(admin);
        userRepository.save(user);
        return "Bearer " + jwt.createAccessToken(user);
    }

    private int bannerCount() throws Exception {
        String body = mockMvc.perform(get("/api/public/ads").param("placement", "BANNER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).size();
    }

    @Test
    void registeredBannerStaysHiddenUntilTheSwitchIsTurnedOn() throws Exception {
        String admin = auth(true);
        Instant now = Instant.now();
        String ad = "{\"title\":\"수험표 할인\",\"creativeUrl\":\"https://cdn.example.com/b.webp\","
                + "\"destinationUrl\":\"https://example.com\",\"placement\":\"BANNER\","
                + "\"startsAt\":\"" + now.minus(1, ChronoUnit.HOURS) + "\",\"endsAt\":\"" + now.plus(1, ChronoUnit.DAYS) + "\","
                + "\"active\":true}";
        mockMvc.perform(post("/api/admin/ads").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(ad)).andExpect(status().isCreated());

        JsonNode state = mapper.readTree(mockMvc.perform(get("/api/admin/ads/switch").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(state.get("enabled").asBoolean()).isFalse();
        assertThat(bannerCount()).isZero();

        mockMvc.perform(put("/api/admin/ads/switch").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        assertThat(bannerCount()).isEqualTo(1);

        mockMvc.perform(put("/api/admin/ads/switch").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(bannerCount()).isZero();
    }

    @Test
    void onlyAdminsCanFlipTheSwitch() throws Exception {
        mockMvc.perform(put("/api/admin/ads/switch").header("Authorization", auth(false))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        assertThat(adSwitchService.isEnabled()).isFalse();
    }

    @Test
    void uploadedCreativeIsServedPubliclyAndNonImagesAreRejected() throws Exception {
        String admin = auth(true);
        String body = mockMvc.perform(post("/api/admin/ads/creatives").header("Authorization", admin)
                        .contentType(MediaType.IMAGE_PNG).content(PNG))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String path = mapper.readTree(body).get("path").asText();

        byte[] served = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);

        // 이름만 PNG인 다른 파일(예: SVG·HTML)은 받지 않는다.
        mockMvc.perform(post("/api/admin/ads/creatives").header("Authorization", admin)
                        .contentType(MediaType.IMAGE_PNG).content("<svg onload=alert(1)>".getBytes()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/ads/creatives").header("Authorization", auth(false))
                        .contentType(MediaType.IMAGE_PNG).content(PNG))
                .andExpect(status().isForbidden());
    }
}
