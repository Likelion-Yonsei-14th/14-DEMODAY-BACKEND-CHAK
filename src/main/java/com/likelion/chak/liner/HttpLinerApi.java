package com.likelion.chak.liner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 라이너 플랫폼(https://platform.liner.com/api/v1) 호출. 키는 시크릿 LINER_API_KEY에서 읽고 로그에 남기지 않는다.
 * 글 생성은 Claude 라우터를 비용 우선으로 쓴다(대부분 가장 싼 모델이 답한다).
 */
@Component
public class HttpLinerApi implements LinerApi {

    private static final Logger log = LoggerFactory.getLogger(HttpLinerApi.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final String apiKey;

    public HttpLinerApi(@Value("${chak.liner.api-key:}") String apiKey,
                        @Value("${chak.liner.base-url:https://platform.liner.com/api/v1}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(40));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, String dateRange, int maxResults) {
        if (!enabled() || query == null || query.isBlank()) return List.of();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("country_code", "kr");
        body.put("lang", "ko");
        body.put("max_results", Math.max(1, Math.min(20, maxResults)));
        body.put("include_knowledge", false);
        if (dateRange != null) body.put("date_range", dateRange);
        try {
            String raw = client.post().uri("/tools/search/web")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(body))
                    .retrieve().body(String.class);
            JsonNode results = JSON.readTree(raw == null ? "{}" : raw).path("results");
            List<SearchResult> out = new ArrayList<>();
            for (JsonNode r : results) {
                out.add(new SearchResult(text(r, "title"), text(r, "url"), text(r, "hostname"), text(r, "description"), text(r, "date")));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("Liner search failed: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    @Override
    public String complete(String system, List<Turn> messages, int maxTokens) {
        if (!enabled()) return null;
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", "content", system));
        for (Turn t : messages) all.add(Map.of("role", t.role(), "content", t.content()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "liner-claude-router");
        body.put("routing_strategy", "cost");
        body.put("max_tokens", maxTokens);
        body.put("messages", all);
        try {
            String raw = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(body))
                    .retrieve().body(String.class);
            JsonNode content = JSON.readTree(raw == null ? "{}" : raw).path("choices").path(0).path("message").path("content");
            String text = content.isMissingNode() || content.isNull() ? null : content.asString();
            return text == null || text.isBlank() ? null : text.strip();
        } catch (RuntimeException e) {
            log.warn("Liner completion failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asString();
    }
}
