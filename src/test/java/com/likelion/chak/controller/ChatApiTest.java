package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.domain.Discount;
import com.likelion.chak.liner.LinerApi;
import com.likelion.chak.liner.LinerApi.SearchResult;
import com.likelion.chak.repository.DiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DiscountRepository discounts;
    @MockitoBean private LinerApi liner;
    @MockitoBean private AlertNotifier alerts;

    private static String ask(String question) {
        return "{\"messages\":[{\"role\":\"user\",\"content\":\"" + question + "\"}]}";
    }

    private static String ip() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    @BeforeEach
    void setUp() {
        when(liner.enabled()).thenReturn(true);
    }

    private void discount(String brand, String area, boolean sponsored, LocalDate end) {
        Discount d = new Discount(brand);
        d.update(brand, "음식·카페", brand + " 음료 20% 할인", null, end, "", area, "", "https://x.example/" + brand,
                null, null, List.of(), null, List.of(), null, null, null, null, true, "테스트");
        d.updatePlacement(sponsored, null, null, null);
        discounts.save(d);
    }

    @Test
    void offTopicQuestionsAreTurnedAwayWithoutSearching() throws Exception {
        when(liner.complete(contains("질문 분류기"), any(), anyInt()))
                .thenReturn("{\"onTopic\":false,\"searchQuery\":\"\",\"wantsDiscounts\":false,\"keywords\":[]}");

        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("파이썬 숙제 대신 해줘")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offTopic").value(true))
                .andExpect(jsonPath("$.discounts.length()").value(0));
        verify(liner, never()).search(anyString(), any(), anyInt());
    }

    @Test
    void answersWithOurDiscountsFirstAndOnlyTrustedSources() throws Exception {
        String tag = "망원" + System.nanoTime();
        discount("일반카페" + tag, tag, false, LocalDate.of(2099, 1, 1));
        discount("제휴카페" + tag, tag, true, LocalDate.of(2099, 1, 1));
        discount("끝난카페" + tag, tag, true, LocalDate.of(2000, 1, 1));
        when(liner.complete(contains("질문 분류기"), any(), anyInt())).thenReturn(
                "결과: {\"onTopic\":true,\"searchQuery\":\"수능 성적 발표일\",\"wantsDiscounts\":true,\"keywords\":[\"" + tag + "\"]}");
        when(liner.search(eq("수능 성적 발표일"), any(), anyInt())).thenReturn(List.of(
                new SearchResult("블로그 글", "https://blog.example.com/a", "blog.example.com", "성적 발표는…", ""),
                new SearchResult("평가원 보도자료", "https://www.kice.re.kr/a", "www.kice.re.kr", "성적 통지일은 12월 …", "2026-11-19"),
                new SearchResult("교육부", "https://www.moe.go.kr/b", "www.moe.go.kr", "…", "")));
        when(liner.complete(contains("수능 도우미"), any(), anyInt())).thenReturn("성적은 12월에 나와요 [1]");

        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("성적 언제 나와? 그리고 근처 카페 할인도")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("성적은 12월에 나와요 [1]"))
                .andExpect(jsonPath("$.discounts.length()").value(2))
                .andExpect(jsonPath("$.discounts[0].brand").value("제휴카페" + tag))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].hostname").value("www.kice.re.kr"));
        verify(liner).complete(argThat(system -> system.contains("제휴카페" + tag) && !system.contains("blog.example.com")), any(), anyInt());
    }

    @Test
    void everyWordMustMatchSoOtherKindsOfStoresAreNotShown() throws Exception {
        String tag = "카페" + System.nanoTime();
        discount("서울" + tag, "서울", false, LocalDate.of(2099, 1, 1));
        discount("전국" + tag, "전국", false, LocalDate.of(2099, 1, 1));
        discount("부산" + tag, "부산", true, LocalDate.of(2099, 1, 1));
        Discount climbing = new Discount("클라이밍" + System.nanoTime());
        climbing.update(climbing.getBrand(), "놀이·체험", "대여 할인", null, null, "", "서울", "", "https://x.example/c",
                null, null, List.of(), null, List.of(), null, null, null, null, true, "테스트");
        climbing.updatePlacement(true, null, null, null);
        discounts.save(climbing);
        when(liner.complete(contains("질문 분류기"), any(), anyInt())).thenReturn(
                "{\"onTopic\":true,\"searchQuery\":\"\",\"wantsDiscounts\":true,\"keywords\":[\"서울\",\"" + tag + "\",\"할인\"]}");
        when(liner.complete(contains("수능 도우미"), any(), anyInt())).thenReturn("카페 혜택이에요");

        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("서울 카페 할인")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discounts.length()").value(2))
                .andExpect(jsonPath("$.discounts[?(@.brand =~ /클라이밍.*/)]").isEmpty())
                .andExpect(jsonPath("$.discounts[?(@.brand =~ /부산.*/)]").isEmpty());
    }

    @Test
    void scheduleQuestionsGetNoDiscounts() throws Exception {
        String tag = "신촌" + System.nanoTime();
        discount("제휴" + tag, tag, true, LocalDate.of(2099, 1, 1));
        when(liner.complete(contains("질문 분류기"), any(), anyInt())).thenReturn(
                "{\"onTopic\":true,\"searchQuery\":\"정시 원서 접수\",\"wantsDiscounts\":false,\"keywords\":[\"" + tag + "\"]}");
        when(liner.search(anyString(), any(), anyInt())).thenReturn(List.of());
        when(liner.complete(contains("수능 도우미"), any(), anyInt())).thenReturn("원서는 12월 말이에요");

        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("정시 원서 언제 내?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discounts.length()").value(0))
                .andExpect(jsonPath("$.sources[0].hostname").value("www.suneung.re.kr"));
    }

    @Test
    void oneVisitorCannotAskTooMuch() throws Exception {
        when(liner.complete(contains("질문 분류기"), any(), anyInt()))
                .thenReturn("{\"onTopic\":false,\"searchQuery\":\"\",\"wantsDiscounts\":false,\"keywords\":[]}");
        String me = ip();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", me).contentType(MediaType.APPLICATION_JSON)
                    .content(ask("안녕"))).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", me).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("안녕")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_CHATS"));
    }

    @Test
    void rejectsLongQuestionsAndAssistantLastTurns() throws Exception {
        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content(ask("가".repeat(501)))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"assistant\",\"content\":\"hi\"}]}")).andExpect(status().isBadRequest());
    }

    @Test
    void saysItIsRestingWhenTheKeyIsMissing() throws Exception {
        when(liner.enabled()).thenReturn(false);
        mockMvc.perform(post("/api/public/chat").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("안녕")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_AVAILABLE"));
    }
}
