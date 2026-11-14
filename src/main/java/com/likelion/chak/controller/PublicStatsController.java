package com.likelion.chak.controller;

import com.likelion.chak.service.AdminStatsService;
import com.likelion.chak.service.AdminStatsService.DailyVisitors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 광고 제안 페이지가 보여 주는 날짜별 방문자 수. 합계 숫자만 내보내고 로그인 없이 읽는다. */
@RestController
@RequestMapping("/api/public/stats")
@RequiredArgsConstructor
public class PublicStatsController {

    static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 28);
    static final Duration CACHE = Duration.ofMinutes(10);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminStatsService statsService;

    private volatile Cached cached;

    /** 10월 28일부터 어제까지(한국 시간). 오늘은 아직 끝나지 않아 넣지 않는다. */
    @GetMapping("/visitors")
    public ResponseEntity<VisitorSeries> visitors() {
        LocalDate lastDay = LocalDate.now(KST).minusDays(1);
        Cached current = cached;
        if (current == null || !current.series().to().equals(lastDay) || current.until().isBefore(Instant.now())) {
            List<DailyVisitors> days = lastDay.isBefore(FIRST_DAY) ? List.of() : statsService.naturalSeries(FIRST_DAY, lastDay);
            current = new Cached(new VisitorSeries(FIRST_DAY, lastDay, days), Instant.now().plus(CACHE));
            cached = current;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE).cachePublic()).body(current.series());
    }

    public record VisitorSeries(LocalDate from, LocalDate to, List<DailyVisitors> days) {}

    private record Cached(VisitorSeries series, Instant until) {}
}
