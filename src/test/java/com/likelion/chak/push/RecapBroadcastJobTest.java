package com.likelion.chak.push;

import com.likelion.chak.alert.AlertNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class RecapBroadcastJobTest {

    private static final WebPushCrypto.VapidKeys SERVER_KEYS = WebPushCrypto.generateVapidKeys();

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("chak.push.vapid-public-key", SERVER_KEYS::publicKey);
        registry.add("chak.push.vapid-private-key", SERVER_KEYS::privateKey);
    }

    @Autowired private PushService pushService;
    @Autowired private PushSubscriptionRepository subscriptions;
    @Autowired private PushNotifier notifier;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private PushGateway gateway;
    @MockitoBean private AlertNotifier alerts;

    private RecapBroadcastJob at(Instant now) {
        return new RecapBroadcastJob(pushService, subscriptions, notifier, jdbc, Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM push_broadcasts");
        subscriptions.deleteAll();
        reset(gateway);
        when(gateway.send(anyString(), anyString(), any())).thenReturn(201);
        String auth = WebPushCrypto.b64(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        for (int i = 0; i < 2; i++) {
            subscriptions.save(new PushSubscription(null, (long) (100 + i),
                    "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), WebPushCrypto.generateVapidKeys().publicKey(), auth));
        }
    }

    @Test
    void sendsOnceAtSixAndNeverAgain() {
        assertThat(at(RecapBroadcastJob.SEND_AT.minusSeconds(60)).run()).isEqualTo(-1);
        verify(gateway, never()).send(anyString(), anyString(), any());

        assertThat(at(RecapBroadcastJob.SEND_AT.plusSeconds(10)).run()).isEqualTo(2);
        verify(gateway, atLeast(2)).send(anyString(), anyString(), any());

        reset(gateway);
        assertThat(at(RecapBroadcastJob.SEND_AT.plusSeconds(40)).run()).isEqualTo(-1);   // 서버가 다시 떠도 두 번 안 감
        verify(gateway, never()).send(anyString(), anyString(), any());
    }

    @Test
    void skipsWhenTheDeployIsTooLate() {
        assertThat(at(RecapBroadcastJob.GIVE_UP_AT).run()).isEqualTo(-1);
        verify(gateway, never()).send(anyString(), anyString(), any());
    }

    @Test
    void opensTheRecap() {
        String json = new String(RecapBroadcastJob.payload(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"url\":\"/recap\"").contains("수고 많았어요");
    }
}
