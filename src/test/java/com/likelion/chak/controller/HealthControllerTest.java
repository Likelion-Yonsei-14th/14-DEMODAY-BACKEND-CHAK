package com.likelion.chak.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void livenessAndDbChecksAreOpenAndHealthyWithAWorkingDatabase() throws Exception {
        mockMvc.perform(get("/api/public/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/public/health/db")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.db").value("UP"));
    }

    @Test
    void reportsDownWhenTheDatabaseRefusesConnections() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("Access denied"));
        var response = new HealthController(broken, Duration.ofSeconds(2)).db();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("status", "DOWN").doesNotContainValue("Access denied");
    }

    @Test
    void reportsDownQuicklyWhenTheDatabaseHangs() throws Exception {
        DataSource hanging = mock(DataSource.class);
        when(hanging.getConnection()).thenAnswer(invocation -> {
            Thread.sleep(30_000);
            return mock(Connection.class);
        });
        var controller = new HealthController(hanging, Duration.ofMillis(500));

        long started = System.nanoTime();
        var response = controller.db();
        long tookMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(tookMs).isLessThan(3_000);
    }

    @Test
    void stuckChecksNeverPileUpThreads() throws Exception {
        DataSource hanging = mock(DataSource.class);
        when(hanging.getConnection()).thenAnswer(invocation -> {
            Thread.sleep(30_000);
            return mock(Connection.class);
        });
        long before = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("health-db-check")).count();
        var controller = new HealthController(hanging, Duration.ofMillis(200));
        for (int i = 0; i < 20; i++) {
            assertThat(controller.db().getStatusCode().value()).isEqualTo(503);
        }
        long checkers = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("health-db-check")).count();
        assertThat(checkers - before).isLessThanOrEqualTo(2);
    }
}
