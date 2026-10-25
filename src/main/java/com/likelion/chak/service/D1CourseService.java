package com.likelion.chak.service;

import com.likelion.chak.domain.D1CourseResponse;
import com.likelion.chak.dto.MinigameDtos.D1CourseRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.D1CourseResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 수능 끝 D+1 코스 짜기의 응답을 통계용으로 모은다. 선택지 id는 프런트 {@code d1CourseConfig.ts}와 {@code d1CourseRegions.ts}의
 * id와 같아야 하고, 프런트에 선택지를 추가하면 여기도 같이 늘려야 한다(모르는 id는 거절해서 엉뚱한 값이 통계에 섞이지 않게 한다).
 */
@Service
@RequiredArgsConstructor
public class D1CourseService {

    public static final Set<String> REGIONS = Set.of(
            "seoul", "busan", "daegu", "incheon", "gwangju", "daejeon", "ulsan", "sejong", "gyeonggi",
            "gangwon", "chungbuk", "chungnam", "jeonbuk", "jeonnam", "gyeongbuk", "gyeongnam", "jeju");
    public static final Set<String> AREAS = Set.of("hongdae", "gangnam", "jamsil", "seongsu", "sinchon", "konkuk", "local");
    public static final Set<String> ACTIVITIES = Set.of(
            "dye", "lens", "photo", "clothes", "karaoke", "escape", "themepark", "food", "license");
    public static final Set<String> COMPANIONS = Set.of("alone", "small", "group", "family");
    public static final Set<String> BUDGETS = Set.of("under30k", "30kTo100k", "over100k");

    private final D1CourseResponseRepository repository;

    /** 새 응답이면 true, 같은 응답을 다시 받아 공유 표시만 올렸으면 false. */
    @Transactional
    public boolean record(D1CourseRequest request) {
        validate(request);
        String id = request.responseId().toLowerCase();
        D1CourseResponse existing = repository.findByResponseId(id).orElse(null);
        if (existing != null) {
            applyShared(existing, request);
            return false;
        }
        List<String> a = request.activities();
        String district = request.startDistrict() == null || request.startDistrict().isBlank() ? null : request.startDistrict().strip();
        try {
            repository.saveAndFlush(new D1CourseResponse(id, request.startRegion(), district, request.area(),
                    a.get(0), a.get(1), a.get(2), request.companion(), request.budget(), request.shared()));
            return true;
        } catch (DataIntegrityViolationException e) {
            // 처음 응답과 공유 표시가 거의 동시에 도착해 같은 번호가 이미 들어갔다: 그 줄에 공유 표시만 올린다.
            repository.findByResponseId(id).ifPresent(row -> applyShared(row, request));
            return false;
        }
    }

    private static void applyShared(D1CourseResponse row, D1CourseRequest request) {
        if (request.shared()) {
            row.markShared();
        }
    }

    private static void validate(D1CourseRequest request) {
        boolean ok = REGIONS.contains(request.startRegion())
                && AREAS.contains(request.area())
                && COMPANIONS.contains(request.companion())
                && BUDGETS.contains(request.budget())
                && ACTIVITIES.containsAll(request.activities())
                && new HashSet<>(request.activities()).size() == 3
                // 세종은 시·군·구 단계가 없다.
                && !("sejong".equals(request.startRegion()) && request.startDistrict() != null && !request.startDistrict().isBlank());
        if (!ok) {
            throw new CustomException(ErrorCode.INVALID_D1_COURSE_RESPONSE);
        }
    }
}
