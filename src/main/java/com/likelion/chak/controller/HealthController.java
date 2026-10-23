package com.likelion.chak.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code GET /api/public/health}: 프로세스가 떠 있는지만 본다(서버 자동 교체와 롤백이 쓰므로 DB와 무관하게 유지).
 * {@code GET /api/public/health/db}: DB에서 실제로 쿼리가 되는지 본다. 외부 감시(UptimeRobot)는 이쪽을 보게 해서,
 * 서버는 살아 있는데 DB 연결이 전부 막힌 장애(2026-10-22)를 바로 잡는다. 안 되면 {@code 503}.
 */
@RestController
@RequestMapping("/api/public/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final Duration timeout;
    // 점검이 멈춰도 스레드가 무한정 늘지 않도록 두 개만 쓰고, 둘 다 막혀 있으면 곧바로 실패로 본다.
    private final ThreadPoolExecutor checker = new ThreadPoolExecutor(
            0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "health-db-check");
                thread.setDaemon(true);
                return thread;
            });

    @Autowired
    public HealthController(DataSource dataSource) {
        this(dataSource, Duration.ofSeconds(4));
    }

    HealthController(DataSource dataSource, Duration timeout) {
        this.dataSource = dataSource;
        this.timeout = timeout;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/db")
    public ResponseEntity<Map<String, String>> db() {
        try {
            Future<?> check = checker.submit(this::ping);
            try {
                check.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                check.cancel(true);
                throw e;
            }
            return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
        } catch (RejectedExecutionException | TimeoutException | ExecutionException e) {
            log.warn("DB health check failed: {}", e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN", "db", "DOWN"));
    }

    private void ping() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout((int) Math.max(1, timeout.toSeconds() - 1));
            statement.execute("SELECT 1");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
