package com.likelion.chak.service;

import com.likelion.chak.domain.ExamCareerRun;
import com.likelion.chak.dto.MinigameDtos.ExamCareerRunRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.ExamCareerRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 수험생 커리어 시뮬레이터 한 판의 시작과 끝을 통계용으로 모은다. 계열과 진학 단계 id는 프런트
 * {@code examCareerConfig.ts}와 같아야 하고, 모르는 값은 거절해 엉뚱한 값이 통계에 섞이지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class ExamCareerRunService {

    public static final Set<String> TRACKS = Set.of("humanities", "science");
    /** 프론트(examCareerTitle.ts)가 만드는 칭호. 자유 입력을 받으면 어드민 통계의 칭호별 집계가 끝없이 늘어난다. */
    public static final Set<String> TITLES = Set.of(
            "현역 SKY 신화", "역전의 명수", "다시 일어선 도전자", "현역 에이스", "한 과목 장인", "극단의 줄타기",
            "자유로운 영혼", "끈기의 재도전러", "버티기 달인", "꾸준한 정석 수험생");
    /** "N수의 전설"(N = 재수 횟수 + 1). 가능한 N은 몇 개뿐이라 집계가 늘어나지 않는다. */
    private static final java.util.regex.Pattern LEGEND_TITLE = java.util.regex.Pattern.compile("^([0-9]{1,2})수의 전설$");

    static boolean isKnownTitle(String title) {
        if (title == null) return false;
        if (TITLES.contains(title)) return true;
        var legend = LEGEND_TITLE.matcher(title);
        return legend.matches() && Integer.parseInt(legend.group(1)) >= 2 && Integer.parseInt(legend.group(1)) <= MAX_ATTEMPT + 1;
    }

    public static final Set<String> ENROLLED = Set.of("sky", "seoul-upper", "seoul-lower", "regional", "provincial", "college", "none");
    static final int MAX_ATTEMPT = 20;

    private final ExamCareerRunRepository repository;

    /** 새 판이면 true, 이미 있는 판에 대한 요청이면 false. 끝난 결과가 실려 오면 처음 한 번만 채운다. */
    @Transactional
    public boolean record(ExamCareerRunRequest request) {
        validate(request);
        String id = request.runId().toLowerCase();
        ExamCareerRun existing = repository.findByRunId(id).orElse(null);
        if (existing != null) {
            applyFinish(existing, request);
            return false;
        }
        ExamCareerRun run = new ExamCareerRun(id, request.track());
        applyFinish(run, request);
        try {
            repository.saveAndFlush(run);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 시작과 끝이 거의 동시에 도착해 같은 번호가 이미 들어갔다: 그 줄에 끝난 결과만 채운다.
            repository.findByRunId(id).ifPresent(row -> applyFinish(row, request));
            return false;
        }
    }

    private static void applyFinish(ExamCareerRun run, ExamCareerRunRequest request) {
        if (request.finished()) {
            run.finish(request.attempt(), BigDecimal.valueOf(request.finalAverage()).setScale(1, RoundingMode.HALF_UP),
                    request.enrolled(), request.title());
        }
    }

    private static void validate(ExamCareerRunRequest request) {
        boolean ok = TRACKS.contains(request.track());
        if (request.finished()) {
            ok = ok && request.attempt() != null && request.attempt() <= MAX_ATTEMPT
                    && request.finalAverage() != null && request.finalAverage() >= 1 && request.finalAverage() <= 9
                    && request.enrolled() != null && ENROLLED.contains(request.enrolled())
                    && isKnownTitle(request.title());
        }
        if (!ok) {
            throw new CustomException(ErrorCode.INVALID_EXAM_CAREER_RUN);
        }
    }
}
