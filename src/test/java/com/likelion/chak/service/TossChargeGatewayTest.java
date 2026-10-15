package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossChargeGatewayTest {

    private static final String URL = "https://toss.test/v1/payments";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TossChargeGateway gateway = new TossChargeGateway(builder.build(), "test_sk_secret", URL);

    @Test
    void sendsBasicAuthAndIdempotencyKeyAndAcceptsDone() {
        server.expect(requestTo(URL + "/confirm"))
                .andExpect(method(HttpMethod.POST))
                // base64("test_sk_secret:")
                .andExpect(header("Authorization", "Basic dGVzdF9za19zZWNyZXQ6"))
                .andExpect(header("Idempotency-Key", "order-1"))
                .andExpect(jsonPath("$.paymentKey").value("pk"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"orderId\":\"order-1\",\"totalAmount\":1000}",
                        MediaType.APPLICATION_JSON));
        assertThatCode(() -> gateway.confirm("pk", "order-1", 1000)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void rejectsDifferentAmountInResponse() {
        server.expect(requestTo(URL + "/confirm"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"orderId\":\"order-1\",\"totalAmount\":500}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));
    }

    @Test
    void maps4xxToDeclinedAnd5xxToConfirmFailed() {
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"REJECT_CARD_PAYMENT\"}"));
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED));
    }

    private static void expectCode(Throwable t, ErrorCode code) {
        org.assertj.core.api.Assertions.assertThat(((CustomException) t).getErrorCode()).isEqualTo(code);
    }

    @Test
    void alreadyProcessedPaymentIsVerifiedByLookupAndAccepted() {
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"ALREADY_PROCESSED_PAYMENT\"}"));
        server.expect(requestTo(URL + "/pk")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"orderId\":\"order-1\",\"totalAmount\":1000}",
                        MediaType.APPLICATION_JSON));
        assertThatCode(() -> gateway.confirm("pk", "order-1", 1000)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void alreadyProcessedButLookupShowsOtherOrderIsRejected() {
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"ALREADY_PROCESSED_PAYMENT\"}"));
        server.expect(requestTo(URL + "/pk"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"orderId\":\"someone-else\",\"totalAmount\":1000}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .satisfies(e -> expectCode(e, ErrorCode.PAYMENT_DECLINED));
    }

    @Test
    void tossTemporaryErrorAndBadCredentialsAreServerSideFailures() {
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"PROVIDER_ERROR\"}"));
        server.expect(requestTo(URL + "/confirm")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"UNAUTHORIZED_KEY\"}"));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .satisfies(e -> expectCode(e, ErrorCode.PAYMENT_CONFIRM_FAILED));
        assertThatThrownBy(() -> gateway.confirm("pk", "order-1", 1000))
                .satisfies(e -> expectCode(e, ErrorCode.PAYMENT_CONFIRM_FAILED));
    }

    @Test
    void blankSecretKeyMeansNotConfigured() {
        TossChargeGateway unconfigured = new TossChargeGateway(RestClient.create(), "", URL);
        assertThatThrownBy(() -> unconfigured.confirm("pk", "order-1", 1000))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_CONFIGURED));
    }
}
