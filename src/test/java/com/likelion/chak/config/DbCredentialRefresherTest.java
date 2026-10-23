package com.likelion.chak.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DbCredentialRefresherTest {

    private HikariDataSource pool(String password) {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl("jdbc:h2:mem:refresher;MODE=MySQL");
        pool.setUsername("admin");
        pool.setPassword(password);
        pool.setInitializationFailTimeout(-1);
        return pool;
    }

    @Test
    void updatesThePoolPasswordWhenTheSecretRotated() {
        HikariDataSource pool = pool("old-password");
        var refresher = new DbCredentialRefresher(pool, "rds!db-x",
                id -> "{\"username\":\"admin\",\"password\":\"new-password\"}");

        refresher.refresh();

        assertThat(pool.getPassword()).isEqualTo("new-password");
    }

    @Test
    void leavesThePoolAloneWhenNothingChangedOrTheSecretIsUnreadable() {
        HikariDataSource pool = pool("same-password");
        AtomicReference<String> answer = new AtomicReference<>("{\"username\":\"admin\",\"password\":\"same-password\"}");
        var refresher = new DbCredentialRefresher(pool, "rds!db-x", id -> {
            if (answer.get() == null) throw new IllegalStateException("AccessDenied");
            return answer.get();
        });

        refresher.refresh();
        assertThat(pool.getPassword()).isEqualTo("same-password");

        answer.set(null);
        refresher.refresh();
        assertThat(pool.getPassword()).isEqualTo("same-password");

        answer.set("{\"username\":\"admin\"}");
        refresher.refresh();
        assertThat(pool.getPassword()).isEqualTo("same-password");
    }
}
