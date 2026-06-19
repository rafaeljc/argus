package io.github.rafaeljc.argus.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class FlywayMigrationIT {

    private static final List<String> EXPECTED_TABLES = List.of(
            "users",
            "sessions",
            "email_verifications",
            "password_resets",
            "symbols",
            "price_history",
            "backfill_jobs",
            "transactions",
            "holdings",
            "portfolio_snapshots",
            "alert_rules",
            "alert_firings",
            "outbox",
            "eod_pipeline_runs",
            "admin_audit_log");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migration_appliesV1_createsAllSpecTables() {
        List<String> publicTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);

        assertThat(publicTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void flywayHistory_recordsV1Migration() {
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class);

        assertThat(successCount).isEqualTo(1);
    }
}
