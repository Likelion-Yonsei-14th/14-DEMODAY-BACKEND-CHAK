package com.likelion.chak.push;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.CallerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

/**
 * 답장 알림(웹 푸시) 구독 관리. VAPID 키가 설정돼 있지 않으면 기능 전체가 꺼진다(구독을 받지 않고 알림도 보내지 않는다).
 * 구독 주소는 서버가 직접 호출하는 곳이라, 알려진 푸시 서비스의 주소만 받아 서버가 엉뚱한 곳을 호출하지 못하게 한다.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    static final int MAX_PER_IDENTITY = 5;
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private final PushSubscriptionRepository repository;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final boolean enabled;

    @Autowired
    public PushService(
            PushSubscriptionRepository repository,
            @Value("${chak.push.vapid-public-key:}") String publicKey,
            @Value("${chak.push.vapid-private-key:}") String privateKey,
            @Value("${chak.push.subject:mailto:chak.live.team@gmail.com}") String subject) {
        this.repository = repository;
        this.publicKey = publicKey.strip();
        this.privateKey = privateKey.strip();
        this.subject = subject.strip();
        this.enabled = keysLookValid(this.publicKey, this.privateKey);
        if (!this.publicKey.isEmpty() && !this.enabled) {
            log.warn("Web push is off: the VAPID keys are set but malformed");
        }
    }

    private static boolean keysLookValid(String publicKey, String privateKey) {
        try {
            return !publicKey.isEmpty() && !privateKey.isEmpty()
                    && WebPushCrypto.fromB64(publicKey).length == 65
                    && WebPushCrypto.fromB64(privateKey).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String publicKey() {
        return publicKey;
    }

    String privateKey() {
        return privateKey;
    }

    String subject() {
        return subject;
    }

    @Transactional
    public void subscribe(CallerIdentity caller, String endpoint, String p256dh, String auth) {
        requireEnabled();
        validate(endpoint, p256dh, auth);
        Long userId = caller.userId();
        Long guestId = caller.user() == null ? caller.guestId() : null;
        PushSubscription existing = repository.findByEndpoint(endpoint).orElse(null);
        if (existing != null) {
            existing.renew(userId, guestId, p256dh, auth);
            return;
        }
        long count = userId != null ? repository.countByUserId(userId) : repository.countByGuestId(guestId);
        if (count >= MAX_PER_IDENTITY) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        repository.save(new PushSubscription(userId, guestId, endpoint, p256dh, auth));
    }

    /** 본인 구독만 지운다. 남의 구독이거나 없으면 아무 일도 하지 않는다. */
    @Transactional
    public void unsubscribe(CallerIdentity caller, String endpoint) {
        repository.findByEndpoint(endpoint).ifPresent(subscription -> {
            boolean mine = caller.userId() != null
                    ? caller.userId().equals(subscription.getUserId())
                    : caller.guestId() != null && caller.guestId().equals(subscription.getGuestId());
            if (mine) {
                repository.delete(subscription);
            }
        });
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new CustomException(ErrorCode.PUSH_NOT_AVAILABLE);
        }
    }

    static void validate(String endpoint, String p256dh, String auth) {
        if (!isKnownPushService(endpoint)) {
            throw new CustomException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
        try {
            byte[] key = WebPushCrypto.fromB64(p256dh);
            byte[] secret = WebPushCrypto.fromB64(auth);
            if (key.length != 65 || key[0] != 4 || secret.length != 16) {
                throw new CustomException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
            }
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
    }

    static boolean isKnownPushService(String endpoint) {
        if (endpoint == null || endpoint.length() > 500) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme())
                    && host != null
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ALLOWED_HOST_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
