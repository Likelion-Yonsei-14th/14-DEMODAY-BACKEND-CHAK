package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiBasicsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownPublicPathReturnsNotFoundJson() throws Exception {
        mockMvc.perform(get("/api/public/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodReturns405Json() throws Exception {
        mockMvc.perform(post("/api/public/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeReturns415Json() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void protectedPathWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/desks/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
