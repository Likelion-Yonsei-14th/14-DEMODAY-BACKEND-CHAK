package com.likelion.chak.alert;

/**
 * User-Agent에서 운영자가 알아야 할 정도(기기·브라우저 종류, 인앱 브라우저 여부)만 뽑는다.
 * 원문 User-Agent는 저장하거나 알림에 싣지 않는다.
 */
public final class BrowserLabel {

    private BrowserLabel() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "알 수 없음";
        }
        String ua = userAgent.toLowerCase();
        String platform = ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("android") ? "Android"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("linux") ? "Linux" : "기타";
        String browser;
        if (ua.contains("kakaotalk")) {
            browser = "카카오톡 인앱";
        } else if (ua.contains("instagram")) {
            browser = "인스타그램 인앱";
        } else if (ua.contains("fban") || ua.contains("fbav")) {
            browser = "페이스북 인앱";
        } else if (ua.contains("naver(inapp") || ua.contains("naver inapp")) {
            browser = "네이버 인앱";
        } else if (ua.contains("line/")) {
            browser = "라인 인앱";
        } else if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("samsungbrowser")) {
            browser = "삼성 인터넷";
        } else if (ua.contains("chrome/") || ua.contains("crios")) {
            browser = "Chrome";
        } else if (ua.contains("firefox") || ua.contains("fxios")) {
            browser = "Firefox";
        } else if (ua.contains("safari/")) {
            browser = "Safari";
        } else {
            browser = "기타";
        }
        return platform + " · " + browser;
    }
}
