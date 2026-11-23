package com.likelion.chak.service;

import java.util.List;
import java.util.Set;

/** 광고 지역 맞춤에 쓰는 시·도 이름. 접속 위치 코드는 {@link RegionNames}로 이름으로 바꿔 맞춘다. */
public final class AdRegions {

    public static final List<String> NAMES = List.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private static final Set<String> ALLOWED = Set.copyOf(NAMES);

    /** 서울로 오가기 쉬운 수도권 외곽. 서울을 노린 광고·혜택은 이 지역 방문자에게도 닿는다. */
    private static final Set<String> SEOUL_COMMUTER_AREAS = Set.of("경기", "인천");

    private AdRegions() {
    }

    public static boolean isAllowed(String name) {
        return ALLOWED.contains(name);
    }

    /**
     * 광고·혜택의 대상 지역(adRegion)이 이 방문자 지역(visitorRegion)에 닿는지.
     * 같은 시·도이거나, 서울 광고를 경기·인천 방문자가 보는 경우(외곽에서도 서울로 오기 쉬워서).
     */
    public static boolean reaches(String adRegion, String visitorRegion) {
        if (adRegion == null || visitorRegion == null) {
            return false;
        }
        return adRegion.equals(visitorRegion)
                || ("서울".equals(adRegion) && SEOUL_COMMUTER_AREAS.contains(visitorRegion));
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
