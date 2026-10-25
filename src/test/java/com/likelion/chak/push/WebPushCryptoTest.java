package com.likelion.chak.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    // RFC 8291 부록 A의 예시 값
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void encryptingTheStandardExampleGivesTheStandardResult() throws Exception {
        byte[] body = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.fromB64(UA_PUBLIC), WebPushCrypto.fromB64(AUTH_SECRET),
                WebPushCrypto.privateKey(WebPushCrypto.fromB64(AS_PRIVATE)),
                WebPushCrypto.fromB64(AS_PUBLIC), WebPushCrypto.fromB64(SALT));

        assertThat(WebPushCrypto.b64(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    void theStandardExampleDecryptsBackToTheMessage() throws Exception {
        byte[] plain = WebPushCrypto.decrypt(WebPushCrypto.fromB64(EXPECTED_BODY),
                WebPushCrypto.privateKey(WebPushCrypto.fromB64(UA_PRIVATE)),
                WebPushCrypto.fromB64(UA_PUBLIC), WebPushCrypto.fromB64(AUTH_SECRET));
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(PLAINTEXT);
    }

    @Test
    void aFreshEncryptionRoundTripsWithRandomKeysAndTheBodyChangesEveryTime() throws Exception {
        var browser = WebPushCrypto.generateVapidKeys();                          // 받는 브라우저의 키 쌍 역할
        byte[] auth = WebPushCrypto.fromB64(AUTH_SECRET);
        byte[] message = "{\"title\":\"착\",\"body\":\"응원에 답장이 도착했어요\"}".getBytes(StandardCharsets.UTF_8);

        byte[] first = WebPushCrypto.encrypt(message, browser.publicKey(), AUTH_SECRET);
        byte[] second = WebPushCrypto.encrypt(message, browser.publicKey(), AUTH_SECRET);

        assertThat(first).isNotEqualTo(second);
        byte[] plain = WebPushCrypto.decrypt(first, WebPushCrypto.privateKey(WebPushCrypto.fromB64(browser.privateKey())),
                WebPushCrypto.fromB64(browser.publicKey()), auth);
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(new String(message, StandardCharsets.UTF_8));
    }

    @Test
    void anotherBrowserCannotReadTheMessage() throws Exception {
        var mine = WebPushCrypto.generateVapidKeys();
        var other = WebPushCrypto.generateVapidKeys();
        byte[] body = WebPushCrypto.encrypt("비밀".getBytes(StandardCharsets.UTF_8), mine.publicKey(), AUTH_SECRET);

        assertThatThrownBy(() -> WebPushCrypto.decrypt(body, WebPushCrypto.privateKey(WebPushCrypto.fromB64(other.privateKey())),
                WebPushCrypto.fromB64(other.publicKey()), WebPushCrypto.fromB64(AUTH_SECRET)))
                .isInstanceOf(java.security.GeneralSecurityException.class);
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[WebPushCrypto.MAX_PLAINTEXT + 1], mine.publicKey(), AUTH_SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedKeysHaveTheRightShape() {
        var keys = WebPushCrypto.generateVapidKeys();
        assertThat(WebPushCrypto.fromB64(keys.publicKey())).hasSize(65).startsWith(4);
        assertThat(WebPushCrypto.fromB64(keys.privateKey())).hasSize(32);
        assertThat(WebPushCrypto.generateVapidKeys().publicKey()).isNotEqualTo(keys.publicKey());
    }

    @Test
    void vapidHeaderIsASignedTokenForTheServiceAndVerifiesWithThePublicKey() throws Exception {
        var keys = WebPushCrypto.generateVapidKeys();
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc", "mailto:chak.live.team@gmail.com",
                keys.publicKey(), keys.privateKey(), now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys.publicKey());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(new String(WebPushCrypto.fromB64(parts[0]), StandardCharsets.UTF_8)).contains("\"alg\":\"ES256\"");
        String claims = new String(WebPushCrypto.fromB64(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond())
                .contains("\"sub\":\"mailto:chak.live.team@gmail.com\"");

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(WebPushCrypto.publicKey(WebPushCrypto.fromB64(keys.publicKey())));
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(WebPushCrypto.fromB64(parts[2]))).isTrue();
        assertThat(Arrays.asList(parts)).hasSize(3);
        assertThat(((ECPublicKey) WebPushCrypto.publicKey(WebPushCrypto.fromB64(keys.publicKey()))).getW()).isNotNull();
    }
}
