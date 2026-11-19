package com.likelion.chak.config;

import com.likelion.chak.service.DiscountCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** 매일 오전 10시와 오후 4시에 새 수험생 혜택 후보를 찾아 혜택함에 숨김으로 넣는다. */
@Configuration
@ConditionalOnProperty(name = "chak.discount-collector.enabled", havingValue = "true", matchIfMissing = true)
public class DiscountCollectorJob {

    private static final Logger log = LoggerFactory.getLogger(DiscountCollectorJob.class);

    private final DiscountCollector collector;

    public DiscountCollectorJob(DiscountCollector collector) {
        this.collector = collector;
    }

    @Scheduled(cron = "${chak.discount-collector.cron:0 0 10,16 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            collector.collect();
        } catch (RuntimeException e) {
            log.warn("Discount collector failed: {}", e.getClass().getSimpleName());
        }
    }
}
