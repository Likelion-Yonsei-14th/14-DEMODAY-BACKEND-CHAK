package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 구글 OAuth 2.0 인가 코드 흐름. 코드를 토큰으로 교환(서버 간 TLS 통신)한 뒤 userinfo로 사용자 정보를 받는다.
 * 사용자는 변하지 않는 sub로 식별하며, 이메일은 구글이 검증한 것(email_verified)만 저장한다.
 */
@Component
public class GoogleClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DISPLAY_NAME = 50;
    private static final int MAX_PICTURE_URL = 1000;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userInfoUri;

    @Autowired
    public GoogleClient(
            @Value("${google.client-id:}") String clientId,
            @Value("${google.client-secret:}") String clientSecret,
            @Value("${google.token-uri}") String tokenUri,
            @Value("${google.user-info-uri}") String userInfoUri) {
        this(RestClient.builder().requestFactory(timeouts()).build(), clientId, clientSecret, tokenUri, userInfoUri);
    }

    GoogleClient(RestClient restClient, String clientId, String clientSecret, String tokenUri, String userInfoUri) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuthProfile authenticate(String code, String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.error("Google login is not configured (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)");
            throw new CustomException(ErrorCode.GOOGLE_API_ERROR);
        }
        try {
            return requestProfile(requestAccessToken(code, redirectUri));
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Google API unreachable or malformed response", e);
            throw new CustomException(ErrorCode.GOOGLE_API_ERROR);
        }
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        JsonNode response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = text(response, "access_token");
        if (accessToken == null) {
            throw new IllegalStateException("Google token response has no access token");
        }
        return accessToken;
    }

    private OAuthProfile requestProfile(String accessToken) {
        JsonNode response = restClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);
        String sub = text(response, "sub");
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("Google userinfo has no sub");
        }

        String name = text(response, "name");
        if (name == null || name.isBlank()) {
            name = text(response, "given_name");
        }
        if (name == null || name.isBlank()) {
            name = "구글 사용자";
        }
        name = name.strip();
        if (name.codePointCount(0, name.length()) > MAX_DISPLAY_NAME) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_DISPLAY_NAME));
        }

        boolean verified = response.path("email_verified").asBoolean(false);
        String email = verified ? text(response, "email") : null;
        String picture = text(response, "picture");
        if (picture != null && picture.length() > MAX_PICTURE_URL) {
            picture = null;
        }
        return new OAuthProfile(AuthProvider.GOOGLE, sub, name, email, picture);
    }

    /**
     * 사용자가 낼 수 있는 문제(만료·재사용된 코드, 잘못된 redirect_uri)만 401로 돌려주고,
     * 우리 설정 문제(잘못된 클라이언트 ID/시크릿)나 구글 장애는 502로 돌려주며 로그에 남긴다.
     */
    private CustomException map(RestClientResponseException e) {
        String error = errorCode(e);
        if (e.getStatusCode().is4xxClientError() && ("invalid_grant".equals(error) || "redirect_uri_mismatch".equals(error))) {
            return new CustomException(ErrorCode.INVALID_GOOGLE_AUTH_CODE);
        }
        log.error("Google API rejected the request: status={} error={}", e.getStatusCode().value(), error);
        return new CustomException(ErrorCode.GOOGLE_API_ERROR);
    }

    private String errorCode(RestClientResponseException e) {
        try {
            return JSON.readTree(e.getResponseBodyAsString()).path("error").asString("");
        } catch (RuntimeException parseFailure) {
            return "";
        }
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
