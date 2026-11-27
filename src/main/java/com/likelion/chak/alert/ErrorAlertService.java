package com.likelion.chak.alert;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 오류 알림의 폭주를 막고 비동기로 전달한다.
 * <ul>
 *   <li>같은 오류(fingerprint)는 5분에 한 번만 보내고, 그 사이 발생 횟수는 다음 알림에 합쳐서 알린다.</li>
 *   <li>전체 알림은 분당 20건으로 제한한다(디스코드 제한과 스팸 방지).</li>
 *   <li>누구나 보낼 수 있는 브라우저 오류 제보는 따로 분당 10건만 받는다. 제보를 쏟아부어 서버·결제 오류 알림의
 *       한도를 다 써 버리지 못하게 한도와 묶음 기록을 서버 알림과 나눈다.</li>
 *   <li>전달은 별도 스레드에서 하므로 요청 처리를 지연시키거나 실패시키지 않는다.</li>
 * </ul>
 */
@Service
public class ErrorAlertService {

    static final long WINDOW_MS = 5 * 60 * 1000L;
    static final int MAX_PER_MINUTE = 20;
    static final int MAX_USER_REPORTED_PER_MINUTE = 10;
    private static final int MAX_TRACKED = 500;

    private final AlertNotifier notifier;
    private final Executor executor;
    private final Clock clock;
    private final Budget serverBudget = new Budget(MAX_PER_MINUTE);
    private final Budget userReportedBudget = new Budget(MAX_USER_REPORTED_PER_MINUTE);

    @Autowired
    public ErrorAlertService(AlertNotifier notifier) {
        this(notifier, new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(50),
                runnable -> {
                    Thread thread = new Thread(runnable, "error-alert");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy()), Clock.systemUTC());
    }

    ErrorAlertService(AlertNotifier notifier, Executor executor, Clock clock) {
        this.notifier = notifier;
        this.executor = executor;
        this.clock = clock;
    }

    /** 알림을 접수한다. 예외를 던지지 않는다. */
    public void alert(AlertMessage message) {
        try {
            int suppressed = admit(message.userReported() ? userReportedBudget : serverBudget, message.fingerprint());
            if (suppressed < 0) {
                return;
            }
            executor.execute(() -> {
                try {
                    notifier.notify(message, suppressed);
                } catch (RuntimeException ignored) {
                    // 알림 실패가 서비스에 영향을 주면 안 된다.
                }
            });
        } catch (RejectedExecutionException ignored) {
            // 큐가 가득 차면 버린다.
        }
    }

    /** 보내도 되면 직전 이후 묶인 횟수(0 이상), 보내지 않으면 -1. */
    private synchronized int admit(Budget budget, String fingerprint) {
        long now = clock.millis();
        Map<String, Window> windows = budget.windows;
        Deque<Long> recentSends = budget.recentSends;
        Window window = windows.get(fingerprint);
        if (window != null && now - window.start < WINDOW_MS) {
            window.suppressed++;
            return -1;
        }
        while (!recentSends.isEmpty() && now - recentSends.peekFirst() >= 60_000L) {
            recentSends.pollFirst();
        }
        if (recentSends.size() >= budget.perMinute) {
            if (window != null) {
                window.suppressed++;
            }
            return -1;
        }
        int carried = window == null ? 0 : window.suppressed;
        if (windows.size() >= MAX_TRACKED) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MS);
            if (windows.size() >= MAX_TRACKED) {
                windows.clear();
            }
        }
        windows.put(fingerprint, new Window(now));
        recentSends.addLast(now);
        return carried;
    }

    /** 알림 한도와 묶음 기록 한 벌. 서버 알림과 사용자 제보가 서로의 한도를 쓰지 못하게 따로 둔다. */
    private static final class Budget {
        private final int perMinute;
        private final Map<String, Window> windows = new HashMap<>();
        private final Deque<Long> recentSends = new ArrayDeque<>();

        private Budget(int perMinute) {
            this.perMinute = perMinute;
        }
    }

    private static final class Window {
        private final long start;
        private int suppressed;

        private Window(long start) {
            this.start = start;
        }
    }
}
