package com.likelion.chak.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 접속자 구분용 IP. 호출 횟수 제한에만 쓰고 저장하지 않는다.
 *
 * <p>배포 경로는 브라우저 → Vercel(rewrite) → CloudFront → EC2(도커, 보안 그룹이 CloudFront만 허용)다.
 * CloudFront는 받은 X-Forwarded-For 뒤에 자기에게 접속한 주소를 <b>덧붙이기만</b> 하므로, 가장 왼쪽 값은
 * CloudFront를 직접 부르는 사람이 마음대로 넣을 수 있다. 그래서 오른쪽에서부터 믿을 수 있는 프록시가 붙인 칸만 센다.
 * <ul>
 *   <li>Vercel 경유: {@code "<접속자>, <Vercel 출구 IP>"} → 오른쪽에서 두 번째가 접속자.</li>
 *   <li>CloudFront 직접 호출: {@code "<아무 값>..., <호출자 IP>"} → 가장 오른쪽만 믿을 수 있다.</li>
 * </ul>
 * 몇 칸을 믿을지는 {@code chak.client-ip.trusted-hops}(기본 2: Vercel + CloudFront)로 정한다.
 * {@code chak.client-ip.edge-secret}을 정하면, 그 값을 {@code X-Chak-Edge-Secret} 헤더로 보낸 요청(Vercel 경유)만
 * trusted-hops 칸을 믿고, 비밀값이 없는 요청(CloudFront 직접 호출)은 한 칸 덜 믿는다(= CloudFront가 붙인 실제 호출자 IP).
 * 비밀값 없이 trusted-hops=2로 두면 CloudFront를 직접 부르는 사람은 여전히 키를 바꿀 수 있다(남용 완화이지 보안 경계는 아니다).
 *
 * <p>값은 {@code ClientIpFilter}가 요청마다 한 번 계산해 요청 속성에 넣는다(ForwardedHeaderFilter가 헤더를 지우기 전에).
 */
public final class ClientIp {

    /** ClientIpFilter가 계산한 접속자 IP를 담는 요청 속성 이름. */
    public static final String ATTRIBUTE = ClientIp.class.getName() + ".value";
    /** 필터를 거치지 않은 요청(단위 테스트 등)에서 쓰는 기본값. 운영 설정 기본값과 같다. */
    public static final int DEFAULT_TRUSTED_HOPS = 2;

    private ClientIp() {
    }

    public static String of(HttpServletRequest http) {
        Object resolved = http.getAttribute(ATTRIBUTE);
        if (resolved instanceof String value && !value.isEmpty()) {
            return value;
        }
        return resolve(http.getHeader("X-Forwarded-For"), http.getRemoteAddr(), DEFAULT_TRUSTED_HOPS);
    }

    /**
     * 오른쪽에서 {@code trustedHops}번째 X-Forwarded-For 값을 접속자로 본다. 칸 수가 모자라면 남은 것 중 가장 왼쪽을 쓴다.
     * 고른 값이 주소 모양이 아니면 그보다 오른쪽(믿을 수 있는 프록시가 붙인) 값으로 물러서고, 모두 아니면 remoteAddr를 쓴다.
     */
    public static String resolve(String forwardedFor, String remoteAddr, int trustedHops) {
        if (forwardedFor == null || forwardedFor.isBlank() || trustedHops < 1) {
            return remoteAddr;
        }
        String[] entries = forwardedFor.split(",");
        int start = Math.max(0, entries.length - trustedHops);
        for (int i = start; i < entries.length; i++) {
            String candidate = entries[i].trim();
            if (isAddress(candidate)) {
                return candidate;
            }
        }
        return remoteAddr;
    }

    // 숫자·점·콜론·16진 문자로 된 주소만 받는다. 엉뚱한 문자열로 제한 키를 마음대로 늘리지 못하게 한다.
    private static boolean isAddress(String value) {
        return !value.isEmpty() && value.length() <= 45 && value.matches("^[0-9A-Fa-f:.]+$");
    }
}
