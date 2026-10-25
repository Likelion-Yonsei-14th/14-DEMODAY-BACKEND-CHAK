package com.likelion.chak.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시(RFC 8030)에 필요한 암호 처리: 메시지 암호화(RFC 8291, aes128gcm)와 서버 신원 증명(VAPID, RFC 8292).
 * JDK 암호 기능만 쓴다. 표준 문서의 예시 값으로 검증하는 테스트가 있다.
 */
public final class WebPushCrypto {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    /** 알림 한 건의 본문 상한. 푸시 서비스는 보통 4KB까지 받으니 여유를 둔다. */
    public static final int MAX_PLAINTEXT = 3_000;

    private WebPushCrypto() {
    }

    public static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public static byte[] fromB64(String text) {
        return B64D.decode(text);
    }

    // ---------- 키 ----------

    public record VapidKeys(String publicKey, String privateKey) {
    }

    /** 새 VAPID 키 쌍(공개키 65바이트·개인키 32바이트, 둘 다 base64url). */
    public static VapidKeys generateVapidKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            byte[] publicKey = uncompressed((ECPublicKey) pair.getPublic());
            byte[] d = unsigned(((java.security.interfaces.ECPrivateKey) pair.getPrivate()).getS(), 32);
            return new VapidKeys(b64(publicKey), b64(d));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static PrivateKey privateKey(byte[] d) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), p256()));
    }

    static PublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("공개키는 65바이트 비압축 형식이어야 합니다.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(unsigned(key.getW().getAffineX(), 32), 0, out, 1, 32);
        System.arraycopy(unsigned(key.getW().getAffineY(), 32), 0, out, 33, 32);
        return out;
    }

    private static byte[] unsigned(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[length];
        int copy = Math.min(bytes.length, length);
        System.arraycopy(bytes, bytes.length - copy, out, length - copy, copy);
        return out;
    }

    // ---------- 메시지 암호화 (RFC 8291) ----------

    /** 받는 브라우저의 공개키·인증 비밀로 {@code plaintext}를 암호화한 본문. */
    public static byte[] encrypt(byte[] plaintext, String p256dh, String auth) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair ephemeral = generator.generateKeyPair();
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(plaintext, fromB64(p256dh), fromB64(auth), ephemeral.getPrivate(),
                    uncompressed((ECPublicKey) ephemeral.getPublic()), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 키와 salt를 직접 받는 형태(표준 예시로 검증하기 위해 분리). */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret,
                          PrivateKey asPrivate, byte[] asPublic, byte[] salt) throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("알림 본문이 너무 깁니다.");
        }
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asPrivate);
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.UTF_8), uaPublic, asPublic);
        byte[] ikm = hmac(prkKey, concat(keyInfo, new byte[]{1}));

        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), new byte[]{1})), 12);

        // 한 레코드로 끝낸다: 본문 뒤에 마지막 레코드 표시(0x02)를 붙인다.
        byte[] padded = concat(plaintext, new byte[]{2});
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(salt);
        out.writeBytes(ByteBuffer.allocate(4).putInt(RECORD_SIZE).array());
        out.write(asPublic.length);
        out.writeBytes(asPublic);
        out.writeBytes(ciphertext);
        return out.toByteArray();
    }

    /** 받는 쪽 복호화. 테스트에서 암호화가 맞는지 확인하는 데 쓴다. */
    static byte[] decrypt(byte[] body, PrivateKey uaPrivate, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idLength = body[20] & 0xFF;
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + idLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(uaPrivate);
        agreement.doPhase(publicKey(asPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat(concat("WebPush: info\0".getBytes(StandardCharsets.UTF_8), uaPublic, asPublic), new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), new byte[]{1})), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) {
            end--;
        }
        return Arrays.copyOf(padded, end);   // 마지막 구분 바이트(0x02)는 뺀다
    }

    // ---------- 서버 신원 증명 (VAPID, RFC 8292) ----------

    /** {@code Authorization} 헤더 값: {@code vapid t=<서명된 토큰>, k=<공개키>}. */
    public static String vapidAuthorization(String endpoint, String subject, String publicKey, String privateKey, Instant now) {
        try {
            java.net.URI uri = java.net.URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getAuthority();
            String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond()
                    + ",\"sub\":\"" + subject.replace("\"", "") + "\"}").getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + claims;
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey(fromB64(privateKey)));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + signingInput + "." + b64(signature.sign()) + ", k=" + publicKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 도구 ----------

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.length == 0 ? new byte[32] : key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
