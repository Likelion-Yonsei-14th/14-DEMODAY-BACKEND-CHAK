package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private UserAccount user(boolean admin) {
        UserAccount user = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), admin ? "운영자" : "회원", null, null));
        user.updateAdmin(admin);
        return userRepository.save(user);
    }

    private MockHttpServletRequestBuilder as(UserAccount user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.createAccessToken(user));
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        UserAccount member = user(false);
        for (String path : new String[]{"/api/admin/users", "/api/admin/stats/daily", "/api/admin/stats/regions"}) {
            mockMvc.perform(as(member, get(path))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        }
        mockMvc.perform(as(member, post("/api/admin/users/1/withdraw"))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsersAndReadStats() throws Exception {
        UserAccount admin = user(true);
        mockMvc.perform(as(admin, get("/api/admin/users?query=운영자"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(as(admin, get("/api/admin/stats/daily"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(14))
                .andExpect(jsonPath("$.totalUsers").isNumber());
        mockMvc.perform(as(admin, get("/api/admin/stats/regions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(as(admin, get("/api/admin/stats/daily?from=2026-01-10&to=2026-01-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawFlowOverHttpMapsErrors() throws Exception {
        UserAccount admin = user(true);
        UserAccount member = user(false);
        mockMvc.perform(as(admin, post("/api/admin/users/" + admin.getId() + "/withdraw")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_PROTECTED"));
        mockMvc.perform(as(admin, post("/api/admin/users/999999999/withdraw")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(admin, post("/api/admin/users/" + member.getId() + "/withdraw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("탈퇴한 사용자"))
                .andExpect(jsonPath("$.user.deletedAt").isNotEmpty());
    }
}
