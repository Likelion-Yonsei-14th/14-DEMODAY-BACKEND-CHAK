package com.likelion.chak.alert;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordAlertNotifierTest {

    private static final String URL = "https://discord.com/api/webhooks/123/abc";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DiscordAlertNotifier notifier = new DiscordAlertNotifier(builder.build(), URL, "prod");

    private AlertMessage message() {
        return AlertMessage.builder("프론트", "API 오류 500", "fp", AlertMessage.Severity.ERROR)
                .description("@everyone 서버가 응답하지 않습니다")
                .field("화면", "/prototype/my/desk")
                .field("코드", "INTERNAL_SERVER_ERROR")
                .build();
    }

    @Test
    void postsAnEmbedThatCannotMentionAnyone() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andExpect(jsonPath("$.embeds[0].title").value("[prod] 프론트 · API 오류 500"))
                .andExpect(jsonPath("$.embeds[0].fields[0].name").value("화면"))
                .andExpect(content().string(containsString("INTERNAL_SERVER_ERROR")))
                .andRespond(withSuccess());
        notifier.notify(message(), 0);
        server.verify();
    }

    @Test
    void mentionsHowManyMoreTimesItHappened() {
        server.expect(requestTo(URL))
                .andExpect(content().string(containsString("3회 더 발생")))
                .andRespond(withSuccess());
        notifier.notify(message(), 3);
        server.verify();
    }

    @Test
    void discordFailureIsSwallowed() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatCode(() -> notifier.notify(message(), 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonDiscordWebhookUrls() {
        assertThatThrownBy(() -> new DiscordAlertNotifier(RestClient.create(), "https://evil.example/hook", "prod"))
                .isInstanceOf(IllegalStateException.class);
    }
}
