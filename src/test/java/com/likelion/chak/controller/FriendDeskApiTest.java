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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FriendDeskApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private MockHttpServletRequestBuilder as(UserAccount user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.createAccessToken(user));
    }

    private static final String CREATE = """
            {"displayName":"지수","readModeType":"TIME_CAPSULE","capsuleUnlockAt":"2099-01-01T00:00:00Z"}""";

    @Test
    void everyEndpointNeedsLogin() throws Exception {
        mockMvc.perform(post("/api/desks/for-friend").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/claims/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/claims/abc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/desks/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void createPreviewConfirmClaimAndDeleteOverHttp() throws Exception {
        UserAccount creator = user("만든이");
        UserAccount recipient = user("수령자");

        String body = mockMvc.perform(as(creator, post("/api/desks/for-friend")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimToken").isNotEmpty())
                .andExpect(jsonPath("$.supporterToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String claimToken = com.jayway.jsonpath.JsonPath.read(body, "$.claimToken");
        int deskId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(as(recipient, get("/api/claims/" + claimToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("지수"))
                .andExpect(jsonPath("$.creatorName").value("만든이"))
                .andExpect(jsonPath("$.messageCount").value(0));

        mockMvc.perform(as(recipient, post("/api/claims/" + claimToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_CONFIRMATION_REQUIRED"));
        mockMvc.perform(as(recipient, post("/api/claims/" + claimToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmedRecipient\":false}")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(recipient, post("/api/claims/" + claimToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmedRecipient\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desk.id").value(deskId));
        mockMvc.perform(as(recipient, get("/api/claims/" + claimToken))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_AVAILABLE"));

        // 만든 사람은 더 이상 주인이 아니다. 받은 사람만 지울 수 있다.
        mockMvc.perform(as(creator, delete("/api/desks/" + deskId))).andExpect(status().isNotFound());
        mockMvc.perform(as(recipient, delete("/api/desks/" + deskId))).andExpect(status().isNoContent());
    }
}
