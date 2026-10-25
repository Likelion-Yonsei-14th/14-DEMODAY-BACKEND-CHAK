package com.likelion.chak.controller;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Provisions marked test users when a strong server-side key is configured. */
@RestController
@RequestMapping("/api/load-test")
@Profile("loadtest | prod")
@RequiredArgsConstructor
public class LoadTestAccountController {

    private final UserAccountRepository users;
    private final TokenService tokens;

    @Value("${chak.load-test.provision-key:}")
    private String provisionKey;

    public record ProvisionRequest(String runId, int count) {}
    public record ProvisionedAccount(long userId, String accessToken, String refreshToken, long expiresIn) {}

    @PostMapping("/accounts")
    @Transactional
    public ResponseEntity<List<ProvisionedAccount>> provision(
            @RequestHeader(value = "X-Chak-Load-Key", required = false) String providedKey,
            @RequestBody ProvisionRequest request) {
        if (provisionKey.length() < 32 || providedKey == null || !MessageDigest.isEqual(
                provisionKey.getBytes(StandardCharsets.UTF_8), providedKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null || request.runId() == null || !request.runId().matches("[A-Za-z0-9_-]{4,40}")
                || request.count() < 2 || request.count() > 200) {
            return ResponseEntity.badRequest().build();
        }
        List<ProvisionedAccount> result = new ArrayList<>();
        for (int i = 0; i < request.count(); i++) {
            final int accountIndex = i;
            String identity = "loadtest:" + request.runId() + ":" + i;
            UserAccount user = users.findByProviderAndProviderUserId(AuthProvider.TEST, identity)
                    .orElseGet(() -> users.save(UserAccount.create(
                            AuthProvider.TEST, identity, "부하시험 " + accountIndex, null, null)));
            AuthTokenResponse issued = tokens.issue(user);
            result.add(new ProvisionedAccount(user.getId(), issued.accessToken(),
                    issued.refreshToken(), issued.expiresIn()));
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
}
