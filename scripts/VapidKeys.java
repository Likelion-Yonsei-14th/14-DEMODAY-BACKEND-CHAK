import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * 웹 푸시용 VAPID 키 쌍을 새로 만든다. 서버 시크릿(chak/prod/app)에 넣을 값을 그대로 출력한다.
 *   java scripts/VapidKeys.java
 * 개인키는 비밀이라 저장소·채팅에 남기지 말고 곧바로 시크릿에 넣는다. 공개키는 브라우저에 그대로 내려가는 값이다.
 */
public class VapidKeys {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 4;
        System.arraycopy(unsigned(publicKey.getW().getAffineX()), 0, uncompressed, 1, 32);
        System.arraycopy(unsigned(publicKey.getW().getAffineY()), 0, uncompressed, 33, 32);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        System.out.println("VAPID_PUBLIC_KEY=" + b64.encodeToString(uncompressed));
        System.out.println("VAPID_PRIVATE_KEY=" + b64.encodeToString(unsigned(((ECPrivateKey) pair.getPrivate()).getS())));
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }
}
