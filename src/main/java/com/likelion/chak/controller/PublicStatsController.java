package com.likelion.chak.controller;

import com.likelion.chak.service.AdminStatsService;
import com.likelion.chak.service.AdminStatsService.DailyVisitors;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 광고 제안 페이지가 보여 주는 날짜별 방문자 수. 합계 숫자만 내보내고 로그인 없이 읽는다. */
@RestController
@RequestMapping("/api/public/stats")
@RequiredArgsConstructor
public class PublicStatsController {

    static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 28);
    static final Duration CACHE = Duration.ofMinutes(10);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminStatsService statsService;

    /** 계산이 실패하면 이 시간 동안은 다시 계산하지 않는다(실패가 캐시되지 않아 요청마다 전체 집계를 다시 돌리는 것 방지). */
    static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Cached cached;
    private volatile Instant failedUntil = Instant.EPOCH;

    /**
     * 10월 28일부터 어제까지(한국 시간). 오늘은 아직 끝나지 않아 넣지 않는다.
     * 집계는 한 번에 하나만 돈다(single-flight). 다른 요청이 계산 중이면 이전 값을 주고, 이전 값이 없으면 끝날 때까지 기다린다.
     */
    @GetMapping("/visitors")
    public ResponseEntity<VisitorSeries> visitors() {
        LocalDate lastDay = LocalDate.now(KST).minusDays(1);
        Cached current = cached;
        if (!isFresh(current, lastDay)) {
            current = refresh(current, lastDay);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE).cachePublic()).body(current.series());
    }

    private Cached refresh(Cached stale, LocalDate lastDay) {
        boolean locked;
        if (stale != null) {
            locked = refreshLock.tryLock();
            if (!locked) {
                return stale;   // 다른 요청이 계산 중이다
            }
        } else {
            try {
                locked = refreshLock.tryLock(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                locked = false;
            }
            if (!locked) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        try {
            Cached current = cached;
            if (isFresh(current, lastDay)) {
                return current;
            }
            if (Instant.now().isBefore(failedUntil)) {
                if (current != null) return current;
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            try {
                List<DailyVisitors> days = lastDay.isBefore(FIRST_DAY) ? List.of() : statsService.visitorSeries(FIRST_DAY, lastDay);
                current = new Cached(new VisitorSeries(FIRST_DAY, lastDay, days), Instant.now().plus(CACHE));
                cached = current;
                return current;
            } catch (RuntimeException e) {
                failedUntil = Instant.now().plus(FAILURE_BACKOFF);
                if (current != null) return current;
                throw e;
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private static boolean isFresh(Cached current, LocalDate lastDay) {
        return current != null && current.series().to().equals(lastDay) && !current.until().isBefore(Instant.now());
    }

    public record VisitorSeries(LocalDate from, LocalDate to, List<DailyVisitors> days) {}

    private record Cached(VisitorSeries series, Instant until) {}
}
