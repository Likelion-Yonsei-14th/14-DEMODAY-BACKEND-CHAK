package com.likelion.chak;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationTest {

    @Test
    void migrationsCreateCurrentSchemaFromEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var tables = connection.getMetaData().getTables(null, null, "advertisement_events", null);
             var columns = connection.getMetaData().getColumns(
                     null, null, "personal_desks", "default_message_visibility")) {
            assertThat(tables.next()).isTrue();
            assertThat(columns.next()).isTrue();
        }
    }

    @Test
    void legacySchemaCanBeExplicitlyBaselinedAndUpgraded() throws Exception {
        String url = "jdbc:h2:mem:legacy-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        createLegacySchema(url);

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var tables = connection.getMetaData().getTables(null, null, "media_assets", null);
             var columns = connection.getMetaData().getColumns(null, null, "users", "admin")) {
            assertThat(tables.next()).isTrue();
            assertThat(columns.next()).isTrue();
        }
    }

    @Test
    void duplicateLegacyOwnerFailsBeforeFeatureSchemaMutation() throws Exception {
        String url = "jdbc:h2:mem:legacy-duplicate;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        createLegacySchema(url);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, kakao_id, display_name, created_at, updated_at)
                    VALUES (1, 'duplicate-owner', 'owner', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO personal_desks (
                        owner_id, creator_id, display_name, claim_status, read_mode_type,
                        timezone, public_feed_enabled, room_closed, supporter_token,
                        created_at, updated_at
                    ) VALUES
                        (1, 1, 'desk-1', 'CLAIMED', 'DAILY', 'Asia/Seoul', TRUE, FALSE,
                         '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        (1, 1, 'desk-2', 'CLAIMED', 'DAILY', 'Asia/Seoul', TRUE, FALSE,
                         '00000000-0000-0000-0000-000000000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        assertThatThrownBy(flyway::migrate).isInstanceOf(RuntimeException.class);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var columns = connection.getMetaData().getColumns(null, null, "users", "admin")) {
            assertThat(columns.next()).isFalse();
        }
    }

    private void createLegacySchema(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__baseline_schema.sql"))
                .execute(dataSource);
    }
}
