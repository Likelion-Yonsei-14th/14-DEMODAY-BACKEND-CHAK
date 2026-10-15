package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleClientTest {

    private static final String TOKEN = "https://google.test/token";
    private static final String USERINFO = "https://google.test/userinfo";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleClient client = new GoogleClient(builder.build(), "cid", "csecret", TOKEN, USERINFO);

    private void tokenOk() {
        server.expect(requestTo(TOKEN)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=cid")))
                .andExpect(content().string(containsString("client_secret=csecret")))
                .andExpect(content().string(containsString("code=the-code")))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
    }

    private void userinfo(String json) {
        server.expect(requestTo(USERINFO)).andExpect(header("Authorization", "Bearer at"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static void assertCode(Throwable t, ErrorCode code) {
        assertThat(((CustomException) t).getErrorCode()).isEqualTo(code);
    }

    @Test
    void exchangesCodeAndMapsProfile() {
        tokenOk();
        userinfo("{\"sub\":\"1234567890\",\"name\":\"홍길동\",\"email\":\"a@b.com\",\"email_verified\":true,\"picture\":\"https://p/1.png\"}");
        OAuthProfile profile = client.authenticate("the-code", "http://localhost:5173/auth/google/callback");
        assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(profile.providerUserId()).isEqualTo("1234567890");
        assertThat(profile.nickname()).isEqualTo("홍길동");
        assertThat(profile.email()).isEqualTo("a@b.com");
        assertThat(profile.profileImageUrl()).isEqualTo("https://p/1.png");
        server.verify();
    }

    @Test
    void unverifiedEmailIsNotStored() {
        tokenOk();
        userinfo("{\"sub\":\"1\",\"name\":\"n\",\"email\":\"a@b.com\",\"email_verified\":false}");
        assertThat(client.authenticate("the-code", "r").email()).isNull();
    }

    @Test
    void missingNameFallsBackAndLongNameIsTruncatedToColumnSize() {
        tokenOk();
        userinfo("{\"sub\":\"1\"}");
        assertThat(client.authenticate("the-code", "r").nickname()).isEqualTo("구글 사용자");

        RestClient.Builder b2 = RestClient.builder();
        MockRestServiceServer s2 = MockRestServiceServer.bindTo(b2).build();
        GoogleClient c2 = new GoogleClient(b2.build(), "cid", "csecret", TOKEN, USERINFO);
        s2.expect(requestTo(TOKEN)).andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
        s2.expect(requestTo(USERINFO)).andRespond(withSuccess(
                "{\"sub\":\"1\",\"name\":\"" + "가".repeat(80) + "\"}", MediaType.APPLICATION_JSON));
        assertThat(c2.authenticate("c", "r").nickname()).hasSize(50);
    }

    @Test
    void missingSubIsAnApiError() {
        tokenOk();
        userinfo("{\"name\":\"n\"}");
        assertThatThrownBy(() -> client.authenticate("the-code", "r"))
                .satisfies(e -> assertCode(e, ErrorCode.GOOGLE_API_ERROR));
    }

    @Test
    void invalidGrantMeansInvalidCode() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));
        assertThatThrownBy(() -> client.authenticate("the-code", "r"))
                .satisfies(e -> assertCode(e, ErrorCode.INVALID_GOOGLE_AUTH_CODE));
    }

    @Test
    void badClientCredentialsAreOurConfigurationProblem() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));
        assertThatThrownBy(() -> client.authenticate("the-code", "r"))
                .satisfies(e -> assertCode(e, ErrorCode.GOOGLE_API_ERROR));
    }

    @Test
    void googleOutageIsAnApiError() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.authenticate("the-code", "r"))
                .satisfies(e -> assertCode(e, ErrorCode.GOOGLE_API_ERROR));
    }

    @Test
    void notConfiguredIsAnApiErrorWithoutCallingGoogle() {
        GoogleClient unconfigured = new GoogleClient(RestClient.create(), "", "", TOKEN, USERINFO);
        assertThatThrownBy(() -> unconfigured.authenticate("c", "r"))
                .satisfies(e -> assertCode(e, ErrorCode.GOOGLE_API_ERROR));
    }
}
