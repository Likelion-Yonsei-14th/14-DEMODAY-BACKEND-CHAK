package com.likelion.chak.push;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 답장이 저장된 뒤(커밋 후) 편지를 쓴 사람의 구독으로 알림을 보낸다. 요청 스레드를 붙잡지 않도록 따로 돌리고,
 * 실패해도 답장 자체에는 영향이 없다. 알림에는 답장 내용을 넣지 않는다(잠금 화면에 보이므로).
 */
@Component
public class PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);
    static final int GIVE_UP_AFTER_FAILURES = 5;

    private final PushService pushService;
    private final PushSubscriptionRepository repository;
    private final PushGateway gateway;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500), runnable -> {
                Thread thread = new Thread(runnable, "push-sender");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public PushNotifier(PushService pushService, PushSubscriptionRepository repository, PushGateway gateway) {
        this.pushService = pushService;
        this.repository = repository;
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplyCreated(ReplyCreatedEvent event) {
        if (!pushService.isEnabled()) {
            return;
        }
        List<PushSubscription> subscriptions = new ArrayList<>();
        if (!event.userIds().isEmpty()) {
            subscriptions.addAll(repository.findAllByUserIdIn(event.userIds()));
        }
        if (!event.guestIds().isEmpty()) {
            subscriptions.addAll(repository.findAllByGuestIdIn(event.guestIds()));
        }
        if (subscriptions.isEmpty()) {
            return;
        }
        byte[] payload = payload(event.supporterToken());
        for (PushSubscription subscription : subscriptions) {
            executor.execute(() -> deliver(subscription.getId(), payload));
        }
    }

    /** 한 구독에 알림을 보내고 결과를 기록한다. 테스트에서 직접 부를 수 있다. */
    void deliver(Long subscriptionId, byte[] payload) {
        try {
            PushSubscription subscription = repository.findById(subscriptionId).orElse(null);
            if (subscription == null) {
                return;
            }
            byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());
            String authorization = WebPushCrypto.vapidAuthorization(subscription.getEndpoint(), pushService.subject(),
                    pushService.publicKey(), pushService.privateKey(), Instant.now());
            int status = gateway.send(subscription.getEndpoint(), authorization, body);
            if (status >= 200 && status < 300) {
                subscription.markDelivered(Instant.now());
                repository.save(subscription);
            } else if (status == 404 || status == 410) {
                repository.deleteByEndpoint(subscription.getEndpoint());     // 브라우저가 구독을 지웠다
            } else {
                subscription.markFailed();
                if (subscription.getFailureCount() >= GIVE_UP_AFTER_FAILURES) {
                    repository.deleteByEndpoint(subscription.getEndpoint());
                } else {
                    repository.save(subscription);
                }
            }
        } catch (Exception e) {
            log.warn("Push delivery failed for subscription {}: {}", subscriptionId, e.toString());
        }
    }

    static byte[] payload(String supporterToken) {
        String json = "{\"title\":\"착\",\"body\":\"응원에 답장이 도착했어요\",\"url\":\"/desk/" + supporterToken + "/replies\",\"tag\":\"reply\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
