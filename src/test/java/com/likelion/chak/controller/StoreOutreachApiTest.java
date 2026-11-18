package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StoreOutreachApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void adminsShareOutreachRecords() throws Exception {
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "다은", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        String auth = "Bearer " + jwt.createAccessToken(admin);
        String store = "방탈출" + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(get("/api/admin/store-outreach")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/store-outreach").header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/admin/store-outreach").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"" + store + "\",\"category\":\"오락·체험\",\"region\":\"홍대\",\"channel\":\"INSTAGRAM\",\"contact\":\"@escape\",\"assignee\":\"다은\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.kind").value("STORE"))
                .andExpect(jsonPath("$.updatedByName").value("다은"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(put("/api/admin/store-outreach/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"BRAND\",\"storeName\":\"" + store + "\",\"channel\":\"PHONE\",\"contact\":\"02-123-4567\",\"status\":\"REPLIED\",\"note\":\"다음 주 통화\",\"lastContactedOn\":\"2026-11-17\",\"offerPrice\":79000,\"expectedProfit\":792000,\"unitPrice\":100000,\"proposalUrl\":\"https://www.chak.live/promotion/deck?d=abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerPrice").value(79000))
                .andExpect(jsonPath("$.expectedProfit").value(792000))
                .andExpect(jsonPath("$.unitPrice").value(100000))
                .andExpect(jsonPath("$.proposalUrl").value("https://www.chak.live/promotion/deck?d=abc"))
                .andExpect(jsonPath("$.status").value("REPLIED"))
                .andExpect(jsonPath("$.kind").value("BRAND"))
                .andExpect(jsonPath("$.lastContactedOn").value("2026-11-17"));

        mockMvc.perform(get("/api/admin/store-outreach").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.storeName=='" + store + "')].channel").value("PHONE"));

        mockMvc.perform(post("/api/admin/store-outreach").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storeName\":\" \",\"channel\":\"PHONE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/store-outreach/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeName\":\"" + store + "\",\"channel\":\"PHONE\",\"offerPrice\":-1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/store-outreach/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/store-outreach/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
    }
}
