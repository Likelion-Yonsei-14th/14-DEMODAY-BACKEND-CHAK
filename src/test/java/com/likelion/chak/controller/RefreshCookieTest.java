package com.likelion.chak.controller;

import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private String newRefreshToken(String kakaoId) {
        UserAccount user = userAccountRepository.save(
                UserAccount.createKakao(kakaoId, "테스터", null, null));
        AuthTokenResponse issued = tokenService.issue(user);
        return issued.refreshToken();
    }

    @Test
    void refreshWithCookieRotatesAndResetsCookie() throws Exception {
        String token = newRefreshToken("cookie-refresh-1");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("chak_refresh_token", token))
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("chak_refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    void refreshWithBodyStillWorks() throws Exception {
        String token = newRefreshToken("cookie-refresh-2");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("chak_refresh_token=")));
    }

    @Test
    void cookieRefreshFromUnknownOriginIsRejected() throws Exception {
        String token = newRefreshToken("cookie-refresh-3");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("chak_refresh_token", token))
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshWithoutAnyTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
