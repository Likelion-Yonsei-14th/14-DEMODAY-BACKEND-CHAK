package com.likelion.chak.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영에서만 켜지는 설정(RDS 시크릿 이름이 있을 때 만들어지는 빈)이 실제로 기동하는지 확인한다.
 * 2026-10-23: 이 빈의 생성자 두 개를 스프링이 고르지 못해 운영에서만 앱이 시작되자마자 죽었다(테스트는 이 빈을 만들지 않아 통과했음).
 */
@SpringBootTest(properties = {
        "chak.db-secret-id=rds!db-test-secret",
        "chak.db-secret-refresh-ms=3600000",
})
class ProductionBeansStartTest {

    @Autowired private ApplicationContext context;

    @Test
    void dbCredentialRefresherIsCreatedWhenASecretIdIsConfigured() {
        assertThat(context.getBeansOfType(DbCredentialRefresher.class)).hasSize(1);
    }

    @Test
    void schedulingIsEnabled() {
        assertThat(context.getBeansOfType(SchedulingConfig.class)).hasSize(1);
        assertThat(context.getBeansOfType(DataRetentionJob.class)).hasSize(1);
    }
}
