package com.likelion.chak.service;

import java.util.List;
import java.util.Set;

/** 광고 지역 맞춤에 쓰는 시·도 이름. 접속 위치 코드는 {@link RegionNames}로 이름으로 바꿔 맞춘다. */
public final class AdRegions {

    public static final List<String> NAMES = List.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private static final Set<String> ALLOWED = Set.copyOf(NAMES);

    private AdRegions() {
    }

    public static boolean isAllowed(String name) {
        return ALLOWED.contains(name);
    }

    /** 국내 접속이면 시·도 이름, 해외이거나 모르면 null(전국 광고만 받는다). */
    public static String fromGeo(String country, String region) {
        if (!"KR".equalsIgnoreCase(country) || region == null || region.isBlank()) {
            return null;
        }
        String name = RegionNames.of(country, region);
        return ALLOWED.contains(name) ? name : null;
    }
}
