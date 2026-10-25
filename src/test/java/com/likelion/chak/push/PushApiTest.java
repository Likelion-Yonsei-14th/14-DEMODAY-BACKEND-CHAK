package com.likelion.chak.push;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.AdminUserService;
import com.likelion.chak.service.DeskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PushApiTest {

    private static final WebPushCrypto.VapidKeys SERVER_KEYS = WebPushCrypto.generateVapidKeys();
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/";

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("chak.push.vapid-public-key", SERVER_KEYS::publicKey);
        registry.add("chak.push.vapid-private-key", SERVER_KEYS::privateKey);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private DeskService deskService;
    @Autowired private PushSubscriptionRepository subscriptions;
    @Autowired private PushNotifier notifier;
    @Autowired private AdminUserService adminUserService;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private PushGateway gateway;
    @MockitoBean private AlertNotifier alerts;

    private UserAccount member(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private String auth(UserAccount user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    /** 브라우저가 구독할 때 만드는 키 쌍과 인증 비밀. */
    private record Browser(WebPushCrypto.VapidKeys keys, String auth, String endpoint) {
        static Browser create() {
            return new Browser(WebPushCrypto.generateVapidKeys(), WebPushCrypto.b64(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}),
                    FCM + UUID.randomUUID());
        }

        String subscribeJson() {
            return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + keys.publicKey() + "\",\"auth\":\"" + auth + "\"}}";
        }
    }

    private MockHttpServletRequestBuilder subscribe(UserAccount user, String json) {
        return put("/api/public/push/subscription").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void publishesTheKeyAndSaysWhetherPushWorks() throws Exception {
        mockMvc.perform(get("/api/public/push/key")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.publicKey").value(SERVER_KEYS.publicKey()));
    }

    @Test
    void withoutKeysPushIsOffAndSubscribingIsRefused() {
        var off = new PushService(subscriptions, "", "", "mailto:x@example.com");
        assertThat(off.isEnabled()).isFalse();
        assertThat(new PushService(subscriptions, "not-base64!!", "also bad", "mailto:x@example.com").isEnabled()).isFalse();
        assertThat(new PushService(subscriptions, SERVER_KEYS.publicKey(), SERVER_KEYS.privateKey(), "mailto:x@example.com").isEnabled()).isTrue();
    }

    @Test
    void subscribingNeedsALoginOrAKnownGuestCookie() throws Exception {
        Browser browser = Browser.create();
        mockMvc.perform(put("/api/public/push/subscription").contentType(MediaType.APPLICATION_JSON).content(browser.subscribeJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyKnownPushServicesAndWellFormedKeysAreAccepted() throws Exception {
        UserAccount me = member("구독자");
        Browser browser = Browser.create();
        String[] badEndpoints = {
                "http://fcm.googleapis.com/fcm/send/abc",                    // https가 아님
                "https://evil.example.com/fcm/send/abc",                     // 모르는 서비스
                "https://fcm.googleapis.com.evil.com/send",                  // 이름만 비슷함
                "https://169.254.169.254/latest/meta-data",                  // 서버 내부 주소
                "https://user@fcm.googleapis.com/send",                      // 계정 정보가 붙음
                "https://fcm.googleapis.com:8443/send",                      // 다른 포트
        };
        for (String endpoint : badEndpoints) {
            mockMvc.perform(subscribe(me, "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + browser.keys().publicKey() + "\",\"auth\":\"" + browser.auth() + "\"}}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(subscribe(me, "{\"endpoint\":\"" + browser.endpoint() + "\",\"keys\":{\"p256dh\":\"짧음\",\"auth\":\"" + browser.auth() + "\"}}")).andExpect(status().isBadRequest());
        mockMvc.perform(subscribe(me, "{\"endpoint\":\"" + browser.endpoint() + "\",\"keys\":{\"p256dh\":\"" + browser.keys().publicKey() + "\",\"auth\":\"AAAA\"}}")).andExpect(status().isBadRequest());
        for (String ok : new String[]{"https://updates.push.services.mozilla.com/wpush/v2/x", "https://web.push.apple.com/x", "https://wns2-par02p.notify.windows.com/w/?token=x"}) {
            mockMvc.perform(subscribe(me, "{\"endpoint\":\"" + ok + UUID.randomUUID() + "\",\"keys\":{\"p256dh\":\"" + browser.keys().publicKey() + "\",\"auth\":\"" + browser.auth() + "\"}}"))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void aMemberCanSubscribeMoveAndUnsubscribeAndOneBrowserIsNeverStoredTwice() throws Exception {
        UserAccount me = member("회원");
        UserAccount other = member("다른 회원");
        Browser browser = Browser.create();

        mockMvc.perform(subscribe(me, browser.subscribeJson())).andExpect(status().isNoContent());
        mockMvc.perform(subscribe(me, browser.subscribeJson())).andExpect(status().isNoContent());
        assertThat(subscriptions.countByUserId(me.getId())).isEqualTo(1);

        // 남의 구독은 지울 수 없다
        mockMvc.perform(delete("/api/public/push/subscription").header("Authorization", auth(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + browser.endpoint() + "\"}")).andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(browser.endpoint())).isPresent();

        mockMvc.perform(delete("/api/public/push/subscription").header("Authorization", auth(me)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + browser.endpoint() + "\"}")).andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(browser.endpoint())).isEmpty();
    }

    @Test
    void oneAccountCanHaveAtMostFiveBrowsers() throws Exception {
        UserAccount me = member("여러 기기");
        for (int i = 0; i < 5; i++) mockMvc.perform(subscribe(me, Browser.create().subscribeJson())).andExpect(status().isNoContent());
        mockMvc.perform(subscribe(me, Browser.create().subscribeJson())).andExpect(status().isTooManyRequests());
    }

    @Test
    void aReplyPushesAnEncryptedNotificationWithoutTheReplyTextToTheLetterWriter() throws Exception {
        when(gateway.send(anyString(), anyString(), any())).thenReturn(201);
        UserAccount owner = member("책상 주인");
        UserAccount friend = member("친구");
        deskService.createMyDesk(owner, new DeskCreateRequest("책상", ReadModeType.TIME_CAPSULE, null,
                Instant.parse("2026-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());

        Browser browser = Browser.create();
        mockMvc.perform(subscribe(friend, browser.subscribeJson())).andExpect(status().isNoContent());
        long messageId = mapper.readTree(mockMvc.perform(post("/api/public/desks/" + desk.getSupporterToken() + "/messages")
                .header("Authorization", auth(friend)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"친구\",\"kind\":\"CARD\",\"visibility\":\"PUBLIC\",\"schemaVersion\":1,\"card\":{\"pages\":[{\"id\":\"p1\"}]},"
                        + "\"object\":{\"representationType\":\"LETTER\",\"x\":50,\"y\":50,\"rotation\":0,\"scale\":1,\"zIndex\":1}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/desks/me/replies").header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"SINGLE\",\"targetMessageIds\":[" + messageId + "],\"text\":\"비밀 답장 내용\"}")).andExpect(status().isCreated());

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> authorization = ArgumentCaptor.forClass(String.class);
        verify(gateway, timeout(5_000)).send(org.mockito.ArgumentMatchers.eq(browser.endpoint()), authorization.capture(), body.capture());

        assertThat(authorization.getValue()).startsWith("vapid t=").endsWith(", k=" + SERVER_KEYS.publicKey());
        // 브라우저만 풀 수 있고, 알림에는 답장 내용이 없다
        byte[] plain = WebPushCrypto.decrypt(body.getValue(), WebPushCrypto.privateKey(WebPushCrypto.fromB64(browser.keys().privateKey())),
                WebPushCrypto.fromB64(browser.keys().publicKey()), WebPushCrypto.fromB64(browser.auth()));
        String json = new String(plain, StandardCharsets.UTF_8);
        assertThat(json).contains("응원에 답장이 도착했어요").contains("/desk/" + desk.getSupporterToken() + "/replies").doesNotContain("비밀 답장 내용");
        assertThat(new String(body.getValue(), StandardCharsets.ISO_8859_1)).doesNotContain("답장");
    }

    @Test
    void peopleWithoutASubscriptionAreNotNotifiedAndAFailingPushDoesNotBreakTheReply() throws Exception {
        UserAccount owner = member("책상 주인2");
        UserAccount friend = member("구독 안 한 친구");
        deskService.createMyDesk(owner, new DeskCreateRequest("책상", ReadModeType.TIME_CAPSULE, null,
                Instant.parse("2026-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        PersonalDesk desk = deskService.findByOwnerId(owner.getId());
        long messageId = mapper.readTree(mockMvc.perform(post("/api/public/desks/" + desk.getSupporterToken() + "/messages")
                .header("Authorization", auth(friend)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"친구\",\"kind\":\"CARD\",\"visibility\":\"PUBLIC\",\"schemaVersion\":1,\"card\":{\"pages\":[{\"id\":\"p1\"}]},"
                        + "\"object\":{\"representationType\":\"LETTER\",\"x\":50,\"y\":50,\"rotation\":0,\"scale\":1,\"zIndex\":1}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/desks/me/replies").header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"SINGLE\",\"targetMessageIds\":[" + messageId + "],\"text\":\"답장\"}")).andExpect(status().isCreated());
        verify(gateway, never()).send(anyString(), anyString(), any());
    }

    @Test
    void deliveryResultsDecideWhatHappensToASubscription() {
        UserAccount me = member("결과 확인");
        Browser browser = Browser.create();
        byte[] payload = PushNotifier.payload("token-1");

        // 성공: 전달 시각을 기록한다
        PushSubscription ok = subscriptions.save(new PushSubscription(me.getId(), null, browser.endpoint(), browser.keys().publicKey(), browser.auth()));
        when(gateway.send(anyString(), anyString(), any())).thenReturn(201);
        notifier.deliver(ok.getId(), payload);
        assertThat(subscriptions.findById(ok.getId()).orElseThrow().getLastSuccessAt()).isNotNull();

        // 브라우저가 구독을 지웠다(410): 우리도 지운다
        when(gateway.send(anyString(), anyString(), any())).thenReturn(410);
        notifier.deliver(ok.getId(), payload);
        assertThat(subscriptions.findById(ok.getId())).isEmpty();

        // 서버 오류가 계속되면 5번째에 포기한다
        Browser flaky = Browser.create();
        PushSubscription bad = subscriptions.save(new PushSubscription(me.getId(), null, flaky.endpoint(), flaky.keys().publicKey(), flaky.auth()));
        when(gateway.send(anyString(), anyString(), any())).thenReturn(503);
        for (int i = 0; i < PushNotifier.GIVE_UP_AFTER_FAILURES - 1; i++) notifier.deliver(bad.getId(), payload);
        assertThat(subscriptions.findById(bad.getId())).isPresent();
        notifier.deliver(bad.getId(), payload);
        assertThat(subscriptions.findById(bad.getId())).isEmpty();
    }

    @Test
    void withdrawingAMemberDeletesTheirSubscriptions() throws Exception {
        UserAccount admin = member("운영자");
        admin.updateAdmin(true);
        userRepository.save(admin);
        UserAccount leaving = member("떠나는 회원");
        mockMvc.perform(subscribe(leaving, Browser.create().subscribeJson())).andExpect(status().isNoContent());
        assertThat(subscriptions.countByUserId(leaving.getId())).isEqualTo(1);

        adminUserService.withdraw(admin.getId(), leaving.getId(), true);

        assertThat(subscriptions.countByUserId(leaving.getId())).isZero();
    }
}
