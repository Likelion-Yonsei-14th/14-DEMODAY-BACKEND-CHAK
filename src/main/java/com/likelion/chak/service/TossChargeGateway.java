package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * 토스페이먼츠 결제 승인(POST {base}/confirm). 시크릿 키는 서버에만 둔다.
 * 승인은 됐는데 우리 쪽 반영이 실패한 뒤의 재시도(ALREADY_PROCESSED_PAYMENT)는 결제를 조회해 검증한 뒤 성공으로 취급한다.
 */
@Component
@ConditionalOnProperty(name = "chak.chaltteok.provider", havingValue = "toss")
public class TossChargeGateway implements ChargeGateway {

    private static final Logger log = LoggerFactory.getLogger(TossChargeGateway.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 토스 쪽 일시 오류. 사용자의 결제 문제가 아니므로 같은 주문으로 다시 시도하게 한다. */
    private static final Set<String> RETRYABLE = Set.of(
            "PROVIDER_ERROR", "FAILED_INTERNAL_SYSTEM_PROCESSING",
            "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "UNKNOWN_PAYMENT_ERROR");
    /** 우리 설정(키) 문제. 사용자에게는 결제 실패가 아니라 서버 오류로 보여야 한다. */
    private static final Set<String> MISCONFIGURED = Set.of(
            "UNAUTHORIZED_KEY", "INVALID_API_KEY", "FORBIDDEN_REQUEST", "INCORRECT_BASIC_AUTH_FORMAT");

    private final RestClient restClient;
    private final String secretKey;
    private final String paymentsUrl;

    @Autowired
    public TossChargeGateway(
            @Value("${toss.secret-key:}") String secretKey,
            @Value("${toss.payments-url:https://api.tosspayments.com/v1/payments}") String paymentsUrl) {
        this(RestClient.builder().requestFactory(timeouts()).build(), secretKey, paymentsUrl);
    }

    TossChargeGateway(RestClient restClient, String secretKey, String paymentsUrl) {
        this.restClient = restClient;
        this.secretKey = secretKey;
        this.paymentsUrl = paymentsUrl;
    }

    /** 외부 호출이 무한정 매달려 요청 스레드와 커넥션을 붙잡지 않게 한다. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @Override
    public void confirm(String paymentKey, String orderId, long amount) {
        if (secretKey.isBlank()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        JsonNode body;
        try {
            body = restClient.post()
                    .uri(paymentsUrl + "/confirm")
                    .header("Authorization", authorization())
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String code = errorCode(e);
            log.warn("Toss confirm rejected: orderId={} status={} code={}", orderId, e.getStatusCode().value(), code);
            if ("ALREADY_PROCESSED_PAYMENT".equals(code)) {
                body = lookup(paymentKey, orderId);
            } else {
                throw mapFailure(e, code);
            }
        } catch (RestClientException e) {
            log.warn("Toss confirm unreachable: orderId={}", orderId, e);
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        requireDone(body, orderId, amount);
    }

    /** 이미 승인된 결제를 조회한다. 조회 결과가 DONE이고 주문·금액이 일치할 때만 인정한다. */
    private JsonNode lookup(String paymentKey, String orderId) {
        try {
            return restClient.get()
                    .uri(paymentsUrl + "/{paymentKey}", paymentKey)
                    .header("Authorization", authorization())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Toss lookup failed: orderId={}", orderId, e);
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }

    private void requireDone(JsonNode body, String orderId, long amount) {
        boolean done = body != null
                && "DONE".equals(body.path("status").asString(""))
                && orderId.equals(body.path("orderId").asString(""))
                && body.path("totalAmount").asLong(-1) == amount;
        if (!done) {
            log.warn("Toss confirm unexpected response: orderId={}", orderId);
            throw new CustomException(ErrorCode.PAYMENT_DECLINED);
        }
    }

    private CustomException mapFailure(RestClientResponseException e, String code) {
        if (e.getStatusCode().is5xxServerError() || RETRYABLE.contains(code)) {
            return new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        if (MISCONFIGURED.contains(code) || e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            log.error("Toss rejected our credentials (code={}). Check TOSS_SECRET_KEY.", code);
            return new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        return new CustomException(ErrorCode.PAYMENT_DECLINED);
    }

    private String errorCode(RestClientResponseException e) {
        try {
            return JSON.readTree(e.getResponseBodyAsString()).path("code").asString("");
        } catch (RuntimeException parseFailure) {
            return "";
        }
    }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}
