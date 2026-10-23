package com.likelion.chak.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.function.Function;

/**
 * 실행 중에 RDS 비밀번호가 교체돼도 재시작 없이 새 비밀번호로 이어 가도록 주기적으로 시크릿을 확인한다.
 * 값이 바뀌었으면 풀의 비밀번호를 갱신하고 기존 연결은 사용이 끝나는 대로 새 연결로 교체한다.
 * 시크릿을 읽지 못하면 아무것도 바꾸지 않고 다음 주기에 다시 시도한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression("!'${chak.db-secret-id:}'.isBlank()")
public class DbCredentialRefresher {

    private static final Logger log = LoggerFactory.getLogger(DbCredentialRefresher.class);

    private final DataSource dataSource;
    private final String secretId;
    private final Function<String, String> fetcher;

    public DbCredentialRefresher(
            DataSource dataSource,
            @Value("${chak.db-secret-id}") String secretId,
            @Value("${AWS_REGION:${storage.s3.region:ap-southeast-2}}") String region) {
        this(dataSource, secretId, id -> SecretsManagerEnvironmentPostProcessor.fetch(id, region));
    }

    DbCredentialRefresher(DataSource dataSource, String secretId, Function<String, String> fetcher) {
        this.dataSource = dataSource;
        this.secretId = secretId;
        this.fetcher = fetcher;
    }

    @Scheduled(initialDelayString = "${chak.db-secret-refresh-ms:300000}", fixedDelayString = "${chak.db-secret-refresh-ms:300000}")
    public void refresh() {
        if (!(dataSource instanceof HikariDataSource pool)) {
            return;
        }
        try {
            DbCredentials latest = DbCredentials.parse(fetcher.apply(secretId));
            if (latest.password().equals(pool.getPassword()) && latest.username().equals(pool.getUsername())) {
                return;
            }
            pool.getHikariConfigMXBean().setUsername(latest.username());
            pool.getHikariConfigMXBean().setPassword(latest.password());
            if (pool.getHikariPoolMXBean() != null) {
                pool.getHikariPoolMXBean().softEvictConnections();
            }
            log.info("DB password changed in Secrets Manager: connection pool updated");
        } catch (RuntimeException e) {
            log.warn("Could not refresh DB credentials: {}", e.getClass().getSimpleName());
        }
    }
}
