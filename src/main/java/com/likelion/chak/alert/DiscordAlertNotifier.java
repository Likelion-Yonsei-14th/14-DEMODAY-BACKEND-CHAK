package com.likelion.chak.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 디스코드 웹훅으로 알림을 보낸다. 멘션(@everyone 등)은 막아서 사용자 입력이 핑을 일으키지 못하게 한다.
 * 전달에 실패해도 서비스에는 영향을 주지 않는다(로그만 남김).
 */
@Component
@ConditionalOnExpression("'${chak.alert.discord-webhook-url:}' != ''")
public class DiscordAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordAlertNotifier.class);
    static final String WEBHOOK_PREFIX = "https://discord.com/api/webhooks/";
    private static final int COLOR_ERROR = 0xE5484D;
    private static final int COLOR_WARN = 0xF5A524;

    private final RestClient restClient;
    private final String webhookUrl;
    private final String environment;

    @Autowired
    public DiscordAlertNotifier(
            @Value("${chak.alert.discord-webhook-url}") String webhookUrl,
            @Value("${chak.alert.environment:local}") String environment) {
        this(RestClient.builder().requestFactory(timeouts()).build(), webhookUrl, environment);
    }

    DiscordAlertNotifier(RestClient restClient, String webhookUrl, String environment) {
        if (!webhookUrl.startsWith(WEBHOOK_PREFIX) && !webhookUrl.startsWith("https://discordapp.com/api/webhooks/")) {
            throw new IllegalStateException("DISCORD_WEBHOOK_URL must be a Discord webhook URL");
        }
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
        this.environment = environment;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public void notify(AlertMessage message, int suppressedSinceLast) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(message, suppressedSinceLast))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Discord alert failed: {}", e.toString());
        }
    }

    Map<String, Object> payload(AlertMessage message, int suppressedSinceLast) {
        List<Map<String, Object>> fields = new ArrayList<>();
        message.fields().forEach((name, value) -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", AlertSanitizer.truncate(name, 256));
            field.put("value", AlertSanitizer.truncate(value, 1000));
            field.put("inline", value.length() <= 40);
            fields.add(field);
        });
        if (suppressedSinceLast > 0) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", "묶음");
            field.put("value", "직전 알림 이후 같은 오류가 " + suppressedSinceLast + "회 더 발생했습니다.");
            field.put("inline", false);
            fields.add(field);
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", AlertSanitizer.truncate("[" + environment + "] " + message.source() + " · " + message.title(), 250));
        embed.put("description", AlertSanitizer.truncate(message.description(), 3500));
        embed.put("color", message.severity() == AlertMessage.Severity.ERROR ? COLOR_ERROR : COLOR_WARN);
        embed.put("fields", fields.size() > 25 ? fields.subList(0, 25) : fields);
        embed.put("timestamp", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "CHAK 알림");
        body.put("allowed_mentions", Map.of("parse", List.of()));
        body.put("embeds", List.of(embed));
        return body;
    }
}
