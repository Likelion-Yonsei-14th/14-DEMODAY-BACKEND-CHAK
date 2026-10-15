package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.ChaltteokDtos.ChargeResponse;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.ChaltteokService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChaltteokApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ChaltteokService chaltteokService;

    private UserAccount user() {
        return userAccountRepository.save(
                UserAccount.createKakao("api-" + UUID.randomUUID(), "회원", null, null));
    }

    private MockHttpServletRequestBuilder as(UserAccount user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(user))
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void walletEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/chaltteok/balance")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/chaltteok/purchases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"code\":\"GEM\",\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientBalanceReturns402WithRequiredAndBalanceAtTopLevel() throws Exception {
        UserAccount user = user();
        mockMvc.perform(as(user, post("/api/chaltteok/purchases"))
                        .content("{\"items\":[{\"code\":\"LOCKER_BULB\",\"quantity\":2}]}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CHALTTEOK"))
                .andExpect(jsonPath("$.required").value(400))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void malformedChargeRequestsAreRejectedWithJsonErrors() throws Exception {
        UserAccount user = user();
        for (String body : new String[]{"{}", "{\"amount\":null}", "{\"amount\":\"abc\"}", "{\"amount\":-5}",
                "{\"amount\":99999999999999999999}", "not json"}) {
            mockMvc.perform(as(user, post("/api/chaltteok/charges")).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mockMvc.perform(as(user, post("/api/chaltteok/charges")).content("{\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHARGE_AMOUNT"));
    }

    @Test
    void confirmValidatesOrderIdFormatAndRequiredFields() throws Exception {
        UserAccount user = user();
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm"))
                        .content("{\"paymentKey\":\"pk\",\"orderId\":\"bad id!\",\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm"))
                        .content("{\"orderId\":\"chak-abcdef\",\"amount\":1000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm"))
                        .content("{\"paymentKey\":\"pk\",\"orderId\":\"chak-notmine\",\"amount\":1000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void purchaseRejectsUnknownProductsBadQuantitiesAndBadIdempotencyKey() throws Exception {
        UserAccount user = user();
        mockMvc.perform(as(user, post("/api/chaltteok/purchases"))
                        .content("{\"items\":[{\"code\":\"FREE_STUFF\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/chaltteok/purchases"))
                        .content("{\"items\":[{\"code\":\"GEM\",\"quantity\":-3}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/chaltteok/purchases")).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/chaltteok/purchases")).header("Idempotency-Key", "x")
                        .content("{\"items\":[{\"code\":\"GEM\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void fullFlowOverHttp() throws Exception {
        UserAccount user = user();
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        String confirm = "{\"paymentKey\":\"pk-http\",\"orderId\":\"" + order.orderId() + "\",\"amount\":1000}";
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm")).content(confirm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1000));
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm")).content(confirm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1000));
        mockMvc.perform(as(user, post("/api/chaltteok/charges/confirm"))
                        .content(confirm.replace("1000}", "999}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        mockMvc.perform(as(user, post("/api/chaltteok/purchases")).header("Idempotency-Key", "http-key-0001")
                        .content("{\"items\":[{\"code\":\"GEM\",\"quantity\":10}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spent").value(50))
                .andExpect(jsonPath("$.balance").value(950));
        mockMvc.perform(as(user, get("/api/chaltteok/balance")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(950))
                .andExpect(jsonPath("$.customerKey").isNotEmpty());
        mockMvc.perform(as(user, get("/api/chaltteok/history?size=500")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
