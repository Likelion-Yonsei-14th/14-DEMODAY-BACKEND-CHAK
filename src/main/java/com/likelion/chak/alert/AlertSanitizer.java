package com.likelion.chak.alert;

import java.util.regex.Pattern;

/** 알림에 개인정보나 비밀값이 섞여 나가지 않도록 문자열을 정리한다. */
public final class AlertSanitizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]{5,}\\.[\\w-]{5,}\\.[\\w-]{5,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[\\w.~+/=-]+");
    private static final Pattern LONG_SECRET = Pattern.compile("\\b[A-Za-z0-9_-]{32,}\\b");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private AlertSanitizer() {
    }

    /** 이메일·JWT·Bearer 토큰·긴 비밀값을 가리고 길이를 자른다. */
    public static String clean(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String out = CONTROL.matcher(text).replaceAll(" ");
        out = JWT.matcher(out).replaceAll("[토큰]");
        out = BEARER.matcher(out).replaceAll("Bearer [토큰]");
        out = EMAIL.matcher(out).replaceAll("[이메일]");
        out = LONG_SECRET.matcher(out).replaceAll("[값]");
        return truncate(out.strip(), maxLength);
    }

    /** 경로에서 쿼리·프래그먼트(토큰이 실릴 수 있음)를 제거한다. */
    public static String path(String pathOrUrl, int maxLength) {
        if (pathOrUrl == null) {
            return "";
        }
        String out = pathOrUrl;
        int cut = indexOfAny(out, '?', '#');
        if (cut >= 0) {
            out = out.substring(0, cut);
        }
        int scheme = out.indexOf("://");
        if (scheme >= 0) {
            int slash = out.indexOf('/', scheme + 3);
            out = slash >= 0 ? out.substring(slash) : "/";
        }
        return clean(out, maxLength);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static int indexOfAny(String text, char... chars) {
        int best = -1;
        for (char c : chars) {
            int i = text.indexOf(c);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }
}
