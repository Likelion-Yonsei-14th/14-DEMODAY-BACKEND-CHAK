package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.DeskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인 없이 쓸 수 있는 쓰기 요청의 제한(속도·책상별 상한·비회원 크기). 제한을 기본값 그대로(1배) 돌린다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "chak.limit.scale=1")
class PublicWriteLimitApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DeskService deskService;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private AlertNotifier notifier;

    private PersonalDesk desk() {
        UserAccount owner = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "주인", null, null));
        deskService.createMyDesk(owner, new DeskCreateRequest(
                "책상", ReadModeType.TIME_CAPSULE, null, Instant.parse("2026-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        return deskService.findByOwnerId(owner.getId());
    }

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private static String body(int padding) {
        return "{\"nickname\":\"친구\",\"kind\":\"CARD\",\"visibility\":\"PUBLIC\",\"schemaVersion\":1,"
                + "\"card\":{\"pages\":[{\"id\":\"p1\",\"note\":\"" + "x".repeat(padding) + "\"}]},"
                + "\"object\":{\"representationType\":\"LETTER\",\"x\":50,\"y\":50,\"rotation\":0,\"scale\":1,\"zIndex\":1}}";
    }

    private MockHttpServletRequestBuilder send(PersonalDesk desk, String forwardedFor, String json) {
        MockHttpServletRequestBuilder request = post("/api/public/desks/" + desk.getSupporterToken() + "/messages")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (forwardedFor != null) request.header("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void oneAddressCannotSendMoreThanThirtyGuestMessagesAnHour() throws Exception {
        PersonalDesk desk = desk();
        String flooder = ip();
        for (int i = 0; i < 30; i++) mockMvc.perform(send(desk, flooder, body(0))).andExpect(status().isCreated());

        mockMvc.perform(send(desk, flooder, body(0)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MESSAGES"));
        mockMvc.perform(send(desk, ip(), body(0))).andExpect(status().isCreated());           // 다른 접속자는 영향 없음
    }

    @Test
    void oneGuestCookieIsLimitedEvenWhenTheAddressKeepsChanging() throws Exception {
        PersonalDesk desk = desk();
        MvcResult first = mockMvc.perform(send(desk, ip(), body(0))).andExpect(status().isCreated()).andReturn();
        String cookie = first.getResponse().getCookie("chak_guest_id").getValue();
        // 첫 요청은 쿠키가 아직 없어 세지 않는다. 쿠키를 들고 20건까지는 통과한다.
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(send(desk, ip(), body(0)).cookie(new jakarta.servlet.http.Cookie("chak_guest_id", cookie)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(send(desk, ip(), body(0)).cookie(new jakarta.servlet.http.Cookie("chak_guest_id", cookie)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void oneDeskStopsTakingMessagesWhenFloodedEvenFromManyAddresses() throws Exception {
        PersonalDesk desk = desk();
        for (int i = 0; i < 300; i++) mockMvc.perform(send(desk, ip(), body(0))).andExpect(status().isCreated());

        mockMvc.perform(send(desk, ip(), body(0)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DESK_BUSY"));
        mockMvc.perform(send(desk(), ip(), body(0))).andExpect(status().isCreated());         // 다른 책상은 그대로
    }

    @Test
    void rejectedRequestsDoNotUseUpTheDesksQuota() throws Exception {
        PersonalDesk desk = desk();
        for (int i = 0; i < 40; i++) mockMvc.perform(send(desk, ip(), "{\"kind\":\"CARD\"}")).andExpect(status().isBadRequest());
        for (int i = 0; i < 300; i++) mockMvc.perform(send(desk, ip(), body(0))).andExpect(status().isCreated());
    }

    @Test
    void aGarbageForwardedHeaderCannotCreateNewLimitKeys() throws Exception {
        PersonalDesk desk = desk();
        for (int i = 0; i < 30; i++) mockMvc.perform(send(desk, "evil<" + i + ">", body(0))).andExpect(status().isCreated());
        mockMvc.perform(send(desk, "evil<99>", body(0))).andExpect(status().isTooManyRequests());
    }

    @Test
    void guestCardsAreCappedMuchSmallerThanMemberCards() throws Exception {
        PersonalDesk desk = desk();
        mockMvc.perform(send(desk, ip(), body(120_000)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CARD_PAYLOAD"));
        mockMvc.perform(send(desk, ip(), body(90_000))).andExpect(status().isCreated());

        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        mockMvc.perform(send(desk, ip(), body(120_000)).header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isCreated());
    }

    @Test
    void membersHaveTheirOwnLimit() throws Exception {
        PersonalDesk desk = desk();
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        String auth = "Bearer " + jwt.createAccessToken(member);
        for (int i = 0; i < 60; i++) mockMvc.perform(send(desk, ip(), body(0)).header("Authorization", auth)).andExpect(status().isCreated());
        mockMvc.perform(send(desk, ip(), body(0)).header("Authorization", auth)).andExpect(status().isTooManyRequests());
    }

    @Test
    void reportsAreLimitedPerAddress() throws Exception {
        PersonalDesk desk = desk();
        MvcResult created = mockMvc.perform(send(desk, ip(), body(0))).andExpect(status().isCreated()).andReturn();
        long messageId = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String reporter = ip();
        for (int i = 0; i < 30; i++) {
            int code = mockMvc.perform(post("/api/public/messages/" + messageId + "/report").header("X-Forwarded-For", reporter)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"스팸\"}")).andReturn().getResponse().getStatus();
            assertThat(code).isNotEqualTo(429);
        }
        mockMvc.perform(post("/api/public/messages/" + messageId + "/report").header("X-Forwarded-For", reporter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"스팸\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
