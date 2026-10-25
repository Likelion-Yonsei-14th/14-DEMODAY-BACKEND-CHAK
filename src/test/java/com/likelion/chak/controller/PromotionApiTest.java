package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.PromotionInquiry;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.PromotionInquiryRepository;
import com.likelion.chak.repository.UserAccountRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PromotionApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PromotionInquiryRepository repository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @MockitoBean private AlertNotifier notifier;

    private static String body(String company, String email, String extra) {
        return "{\"company\":\"" + company + "\",\"contactName\":\"김담당\",\"email\":\"" + email
                + "\",\"phone\":\"010-1234-5678\",\"category\":\"카페\",\"interest\":\"PACKAGE\",\"message\":\"문의합니다\",\"consent\":true" + extra + "}";
    }

    private MockHttpServletRequestBuilder submit(String json, String ip) {
        return post("/api/public/promotion/inquiries").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip).content(json);
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void anyoneCanSubmitAndItIsStoredWithLowercasedEmail() throws Exception {
        String company = "브랜드" + unique();
        mockMvc.perform(submit(body(company, "Brand@Example.COM", ""), "10.0.0." + unique().hashCode() % 200))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.received").value(true));
        var saved = repository.findAll().stream().filter(i -> i.getCompany().equals(company)).findFirst().orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("brand@example.com");
        assertThat(saved.getStatus()).isEqualTo(PromotionInquiry.Status.NEW);
    }

    @Test
    void everyProductInterestOnTheFormIsAccepted() throws Exception {
        String[] interests = {"LOCAL_A", "LOCAL_B", "LOCAL_C", "BRAND_S", "BRAND_M", "BRAND_L", "ENTERPRISE", "JOINT", "OTHER"};
        for (String interest : interests) {
            String company = "관심" + unique();
            String json = "{\"company\":\"" + company + "\",\"contactName\":\"박담당\",\"email\":\"i@example.com\",\"interest\":\"" + interest + "\",\"consent\":true}";
            mockMvc.perform(submit(json, "10.7." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200))).andExpect(status().isCreated());
            var saved = repository.findAll().stream().filter(i -> i.getCompany().equals(company)).findFirst().orElseThrow();
            assertThat(saved.getInterest().name()).isEqualTo(interest);
        }
    }

    @Test
    void unknownInterestIsRejected() throws Exception {
        String json = "{\"company\":\"모름\",\"contactName\":\"박담당\",\"email\":\"i@example.com\",\"interest\":\"GOLD\",\"consent\":true}";
        mockMvc.perform(submit(json, "10.8.0.1")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyCompanyContactAndEmailAreRequired() throws Exception {
        String company = "간단" + unique();
        String minimal = "{\"company\":\"" + company + "\",\"contactName\":\"박담당\",\"email\":\"min@example.com\",\"consent\":true}";
        mockMvc.perform(submit(minimal, "10.5.0.1")).andExpect(status().isCreated());
        var saved = repository.findAll().stream().filter(i -> i.getCompany().equals(company)).findFirst().orElseThrow();
        assertThat(saved.getInterest()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getMessage()).isNull();
    }

    @Test
    void rejectsMissingConsentBadEmailAndBadPhone() throws Exception {
        mockMvc.perform(submit(body("회사", "a@b.com", "").replace("\"consent\":true", "\"consent\":false"), "10.1.0.1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(submit(body("회사", "not-an-email", ""), "10.1.0.2")).andExpect(status().isBadRequest());
        mockMvc.perform(submit(body("회사", "a@b.com", "").replace("010-1234-5678", "call me!"), "10.1.0.3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void honeypotAndRepeatedSubmissionsAreAcceptedButNotStored() throws Exception {
        String bot = "봇" + unique();
        mockMvc.perform(submit(body(bot, "bot@example.com", ",\"website\":\"http://spam\""), "10.2.0.1"))
                .andExpect(status().isCreated());
        assertThat(repository.findAll().stream().anyMatch(i -> i.getCompany().equals(bot))).isFalse();

        String company = "중복" + unique();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(submit(body(company, "dup@example.com", ""), "10.2.0.2")).andExpect(status().isCreated());
        }
        assertThat(repository.findAll().stream().filter(i -> i.getCompany().equals(company)).count()).isEqualTo(1);
    }

    @Test
    void limitsOneClientToFiveSubmissionsAnHour() throws Exception {
        String ip = "10.3.0." + (Math.abs(unique().hashCode()) % 250);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(submit(body("한도" + unique(), "limit" + i + unique() + "@example.com", ""), ip)).andExpect(status().isCreated());
        }
        mockMvc.perform(submit(body("한도" + unique(), "limit-over@example.com", ""), ip)).andExpect(status().isTooManyRequests());
    }

    @Test
    void onlyAdminsListAndUpdateInquiries() throws Exception {
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        mockMvc.perform(get("/api/admin/promotion-inquiries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/promotion-inquiries").header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());

        String company = "관리" + unique();
        mockMvc.perform(submit(body(company, "admin-test@example.com", ""), "10.4.0.1")).andExpect(status().isCreated());
        Long id = repository.findAll().stream().filter(i -> i.getCompany().equals(company)).findFirst().orElseThrow().getId();
        String auth = "Bearer " + jwt.createAccessToken(admin);
        mockMvc.perform(get("/api/admin/promotion-inquiries?status=NEW&size=100").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.company=='" + company + "')]").isNotEmpty());
        mockMvc.perform(patch("/api/admin/promotion-inquiries/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED")).andExpect(jsonPath("$.handledAt").isNotEmpty());
        mockMvc.perform(patch("/api/admin/promotion-inquiries/99999999").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isNotFound());
    }
}
