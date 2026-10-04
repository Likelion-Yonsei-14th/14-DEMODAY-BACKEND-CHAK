package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String userInfoUri;

    public KakaoClient(
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret:}") String clientSecret,
            @Value("${kakao.token-uri}") String tokenUri,
            @Value("${kakao.user-info-uri}") String userInfoUri) {
        this.restClient = RestClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    public KakaoProfile authenticate(String code, String redirectUri) {
        validateConfiguration();

        try {
            String kakaoAccessToken = requestAccessToken(code, redirectUri);
            return requestProfile(kakaoAccessToken);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new CustomException(ErrorCode.INVALID_KAKAO_AUTH_CODE);
            }
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        } catch (RestClientException | IllegalStateException e) {
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        JsonNode response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        String accessToken = text(response, "access_token");
        if (accessToken == null) {
            throw new IllegalStateException("Kakao token response has no access token");
        }
        return accessToken;
    }

    private KakaoProfile requestProfile(String accessToken) {
        JsonNode response = restClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Kakao profile response has no id");
        }

        JsonNode account = response.get("kakao_account");
        JsonNode profile = account == null ? null : account.get("profile");
        String nickname = text(profile, "nickname");
        if (nickname == null || nickname.isBlank()) {
            nickname = "카카오 사용자";
        }

        return new KakaoProfile(
                response.get("id").asString(),
                nickname,
                text(account, "email"),
                text(profile, "profile_image_url"));
    }

    private void validateConfiguration() {
        if (clientId.isBlank()) {
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private String text(JsonNode parent, String fieldName) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value == null || value.isNull() ? null : value.asString();
    }
}
