package com.likelion.chak.service;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.domain.Discount;
import com.likelion.chak.liner.LinerApi;
import com.likelion.chak.liner.LinerApi.SearchResult;
import com.likelion.chak.repository.DiscountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class DiscountCollectorTest {

    @Autowired private DiscountCollector collector;
    @Autowired private DiscountRepository discounts;
    @MockitoBean private LinerApi liner;
    @MockitoBean private AlertNotifier alerts;

    @Test
    void addsOnlyNewGroundedCandidatesAsHidden() {
        String tag = String.valueOf(System.nanoTime());
        Discount old = new Discount("기존매장" + tag);
        old.update("기존매장" + tag, "음식·카페", "10%", null, null, "", "전국", "", "https://news.example/old" + tag, null,
                null, List.of(), null, List.of(), null, null, null, null, true, "다은");
        discounts.save(old);

        when(liner.enabled()).thenReturn(true);
        when(liner.search(anyString(), any(), anyInt())).thenReturn(List.of(
                new SearchResult("수험생 할인 모음", "https://news.example/a" + tag, "news.example", "카페 30% …", "2026-11-18"),
                new SearchResult("옛 기사", "https://news.example/old" + tag, "news.example", "…", "")));
        when(liner.complete(anyString(), any(), anyInt())).thenReturn("""
                [{"brand":"새카페%1$s","category":"음식·카페","benefit":"음료 30%% 할인","area":"서울","place":"강남점",
                  "startDate":"2026-11-19","endDate":"2026-12-31","conditions":"수험표 지참","sourceUrl":"https://news.example/a%1$s"},
                 {"brand":"지어낸곳%1$s","category":"음식·카페","benefit":"무료","area":"서울","sourceUrl":"https://made.up/x"},
                 {"brand":"기존매장%1$s","category":"음식·카페","benefit":"20%%","area":"전국","sourceUrl":"https://news.example/a%1$s"},
                 {"brand":"끝난곳%1$s","category":"이상한","benefit":"5%%","area":"화성","endDate":"2020-01-01","sourceUrl":"https://news.example/a%1$s"}]
                """.formatted(tag));

        DiscountCollector.Result result = collector.collect();

        assertThat(result.added()).containsExactly("새카페" + tag);
        Discount added = discounts.findAll().stream().filter(d -> d.getBrand().equals("새카페" + tag)).findFirst().orElseThrow();
        assertThat(added.isVisible()).isFalse();
        assertThat(added.getUpdatedByName()).isEqualTo(DiscountCollector.COLLECTOR_NAME);
        assertThat(added.getArea()).isEqualTo("서울");
        assertThat(added.getSourceUrl()).isEqualTo("https://news.example/a" + tag);
    }

    @Test
    void doesNothingWithoutAKey() {
        when(liner.enabled()).thenReturn(false);
        assertThat(collector.collect().enabled()).isFalse();
    }
}
