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
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var rows = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM admin_invites WHERE email = 'mj0324@yonsei.ac.kr' AND created_by IS NULL")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
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

    @Test
    void chaltteokConstraintsRejectInvalidMoneyStates() throws Exception {
        String url = "jdbc:h2:mem:chaltteok-constraints;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        {
            run(url, "INSERT INTO users (provider_user_id, display_name, created_at, updated_at) VALUES ('k1','a',NOW(),NOW())");
            long uid = queryId(url);
            String wallet = "INSERT INTO chaltteok_wallets (user_id, balance, customer_key, created_at, updated_at) VALUES (%d, %d, '%s', NOW(), NOW())";
            assertThatThrownBy(() -> run(url, wallet.formatted(uid, -1, "ck-neg"))).isInstanceOf(java.sql.SQLException.class);
            run(url, wallet.formatted(uid, 10, "ck-ok"));
            assertThatThrownBy(() -> run(url, wallet.formatted(uid, 0, "ck-dup-user"))).isInstanceOf(java.sql.SQLException.class);

            String charge = "INSERT INTO chaltteok_charges (user_id, order_id, amount, status, payment_key, result_balance, created_at, confirmed_at) VALUES (%d, '%s', %d, '%s', %s, %s, NOW(), %s)";
            assertThatThrownBy(() -> run(url, charge.formatted(uid, "ord-low", 299, "READY", "NULL", "NULL", "NULL"))).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> run(url, charge.formatted(uid, "ord-done", 300, "DONE", "NULL", "NULL", "NULL"))).isInstanceOf(java.sql.SQLException.class);
            run(url, charge.formatted(uid, "ord-1", 300, "DONE", "'pk-1'", "300", "NOW()"));
            assertThatThrownBy(() -> run(url, charge.formatted(uid, "ord-1", 300, "READY", "NULL", "NULL", "NULL"))).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> run(url, charge.formatted(uid, "ord-2", 300, "DONE", "'pk-1'", "300", "NOW()"))).isInstanceOf(java.sql.SQLException.class);

            String ledger = "INSERT INTO chaltteok_ledger (user_id, entry_type, amount, balance_after, ref, created_at) VALUES (%d, '%s', %d, %d, '%s', NOW())";
            assertThatThrownBy(() -> run(url, ledger.formatted(uid, "CHARGE", -5, 0, "r1"))).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> run(url, ledger.formatted(uid, "PURCHASE", 5, 0, "r2"))).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> run(url, ledger.formatted(uid, "PURCHASE", -5, -1, "r3"))).isInstanceOf(java.sql.SQLException.class);
            run(url, ledger.formatted(uid, "CHARGE", 300, 300, "ord-1"));
            assertThatThrownBy(() -> run(url, ledger.formatted(uid, "CHARGE", 300, 600, "ord-1"))).isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void socialLoginMigrationKeepsKakaoMembersAndScopesUniquenessPerProvider() throws Exception {
        String url = "jdbc:h2:mem:social-login;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("7").load().migrate();
        run(url, "INSERT INTO users (kakao_id, display_name, created_at, updated_at) VALUES ('12345','기존','2026-01-01 00:00:00','2026-01-01 00:00:00')");

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement();
             var rs = st.executeQuery("SELECT provider, provider_user_id FROM users")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("KAKAO");
            assertThat(rs.getString(2)).isEqualTo("12345");
        }
        String insert = "INSERT INTO users (provider, provider_user_id, display_name, created_at, updated_at) VALUES ('%s','%s','n',NOW(),NOW())";
        run(url, insert.formatted("GOOGLE", "12345"));
        assertThatThrownBy(() -> run(url, insert.formatted("GOOGLE", "12345"))).isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(() -> run(url, insert.formatted("KAKAO", "12345"))).isInstanceOf(java.sql.SQLException.class);
    }

    private static void run(String url, String sql) throws java.sql.SQLException {
        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static long queryId(String url) throws java.sql.SQLException {
        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement();
             var rs = st.executeQuery("SELECT id FROM users WHERE provider_user_id='k1'")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
