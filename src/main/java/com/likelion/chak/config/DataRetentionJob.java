package com.likelion.chak.config;

import com.likelion.chak.service.DataRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** 사용자가 적은 새벽에 오래된 기록을 지운다. 실패해도 서비스에는 영향이 없고 다음 날 다시 시도한다. */
@Configuration
@ConditionalOnProperty(name = "chak.retention.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);

    private final DataRetentionService retention;

    public DataRetentionJob(DataRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${chak.retention.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            retention.purge();
        } catch (RuntimeException e) {
            log.warn("Data retention failed: {}", e.getClass().getSimpleName());
        }
    }
}
