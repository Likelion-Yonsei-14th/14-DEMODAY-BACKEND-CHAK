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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiscountApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;

    private String body(String brand, boolean visible, String extra) {
        return "{\"brand\":\"" + brand + "\",\"category\":\"놀이·체험\",\"benefit\":\"수험생 50% 할인\","
                + "\"start\":\"2026-11-19\",\"end\":\"2026-12-31\",\"area\":\"서울\",\"place\":\"서울 송파\","
                + "\"sourceUrl\":\"https://www.example.com/news/1\",\"bring\":[\"2027학년도 수험표\",\" \",\"신분증\"],"
                + "\"visible\":" + visible + extra + "}";
    }

    @Test
    void adminsManageDiscountsAndVisitorsSeeOnlyVisibleOnes() throws Exception {
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "다은", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        String auth = "Bearer " + jwt.createAccessToken(admin);
        String brand = "놀이공원" + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(get("/api/admin/discounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/discounts").header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(brand, true, ",\"highlight\":\"50%\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bring.length()").value(2))
                .andExpect(jsonPath("$.bring[1]").value("신분증"))
                .andExpect(jsonPath("$.highlight").value("50%"))
                .andExpect(jsonPath("$.start").value("2026-11-19"))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.updatedByName").value("다은"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        // 사용자 목록: 로그인 없이 보이고, 고친 운영자 이름은 내보내지 않는다.
        mockMvc.perform(get("/api/public/discounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(not(nullValue())))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')].place").value("서울 송파"))
                // 필터는 목록을 돌려준다: 한 건이 있고, 그 이름 칸은 비어 있다.
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')]").value(hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')].updatedByName").value(everyItem(nullValue())));

        // 숨기면 사용자 목록에서 빠지고 어드민에는 남는다.
        mockMvc.perform(put("/api/admin/discounts/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(brand, false, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.highlight").value(nullValue()));
        mockMvc.perform(get("/api/public/discounts"))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')]").value(empty()));
        mockMvc.perform(get("/api/admin/discounts").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.brand=='" + brand + "')].visible").value(false));

        // 잘못된 입력
        mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(" ", true, "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(brand, true, "").replace("\"start\":\"2026-11-19\"", "\"start\":\"2027-01-05\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISCOUNT_PERIOD"));
        mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(brand, true, ",\"image\":\"javascript:alert(1)\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/discounts/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/discounts/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
    }

    @Test
    void extrasCarryEachAdvertisersOwnTerms() throws Exception {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "다은", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        String auth = "Bearer " + jwt.createAccessToken(admin);
        String brand = "카페" + UUID.randomUUID().toString().substring(0, 6);
        String extras = ",\"howTo\":\"네이버 예약 후 방문하면 바로 적용\""
                + ",\"extras\":[{\"label\":\" 증정 \",\"value\":\" 음료 1잔 \"},null,"
                + "{\"label\":\"이용 시간\",\"value\":\"평일 11시~17시\"}]";

        // 저장: 빈 항목은 빠지고 앞뒤 공백은 지워진다. 받는 방법은 50자까지 자유롭게.
        String created = mockMvc.perform(post("/api/admin/discounts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(brand, true, extras)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.howTo").value("네이버 예약 후 방문하면 바로 적용"))
                .andExpect(jsonPath("$.extras.length()").value(2))
                .andExpect(jsonPath("$.extras[0].label").value("증정"))
                .andExpect(jsonPath("$.extras[0].value").value("음료 1잔"))
                .andExpect(jsonPath("$.extras[1].label").value("이용 시간"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        // 사용자 목록에도 같은 순서로 나온다.
        mockMvc.perform(get("/api/public/discounts"))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')].extras[0].value").value("음료 1잔"))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')].extras[1].value").value("평일 11시~17시"));

        // 항목을 비우면 빈 목록, 숨기면 사용자 목록에서 빠진다.
        mockMvc.perform(put("/api/admin/discounts/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(brand, false, ",\"extras\":[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extras").value(empty()));
        mockMvc.perform(get("/api/public/discounts"))
                .andExpect(jsonPath("$.items[?(@.brand=='" + brand + "')]").value(empty()));

        // 줄바꿈·탭(저장 구분자), 한쪽만 빈 항목, 9개 이상은 받지 않는다.
        for (String bad : new String[] {
                "[{\"label\":\"증정\",\"value\":\"음료\\n1잔\"}]",
                "[{\"label\":\"증\\t정\",\"value\":\"음료 1잔\"}]",
                "[{\"label\":\"증정\",\"value\":\" \"}]",
                "[" + String.join(",", java.util.Collections.nCopies(9, "{\"label\":\"a\",\"value\":\"b\"}")) + "]"}) {
            mockMvc.perform(put("/api/admin/discounts/" + id).header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body(brand, true, ",\"extras\":" + bad)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/admin/discounts/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(brand, true, ",\"howTo\":\"" + "가".repeat(51) + "\"")))
                .andExpect(status().isBadRequest());
    }
}
