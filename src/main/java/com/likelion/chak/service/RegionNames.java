package com.likelion.chak.service;

import java.util.Map;

/**
 * 접속 지역 코드를 사람이 읽는 이름으로 바꾼다. 강원·전북처럼 특별자치도로 바뀌며 새 코드가 생긴 곳은 옛 코드와 같은 이름을 준다.
 * 국내인데 모르는 코드는 "기타 (코드 N)"로, 해외는 원래 값을 그대로 보여준다.
 */
public final class RegionNames {

    /** ISO 3166-2:KR 시·도 코드(숫자 부분). */
    private static final Map<String, String> KR = Map.ofEntries(
            Map.entry("11", "서울"), Map.entry("26", "부산"), Map.entry("27", "대구"),
            Map.entry("28", "인천"), Map.entry("29", "광주"), Map.entry("30", "대전"),
            Map.entry("31", "울산"), Map.entry("36", "세종"), Map.entry("41", "경기"),
            Map.entry("42", "강원"), Map.entry("43", "충북"), Map.entry("44", "충남"),
            Map.entry("45", "전북"), Map.entry("46", "전남"), Map.entry("47", "경북"),
            Map.entry("48", "경남"), Map.entry("49", "제주"), Map.entry("50", "세종"),
            Map.entry("51", "강원"), Map.entry("52", "전북"));

    private RegionNames() {
    }

    public static String of(String country, String region) {
        if (region == null || region.isBlank()) {
            return country == null ? "알 수 없음" : country;
        }
        if ("KR".equalsIgnoreCase(country)) {
            String code = region.replaceFirst("(?i)^KR-", "");
            String name = KR.get(code);
            if (name != null) {
                return name;
            }
            return "기타 (코드 " + code + ")";
        }
        return region;
    }
}
