package io.github.rafaeljc.argus.support.containers;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.BeforeTestMethodEvent;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainer {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    private static final String SELECT_APP_TABLES =
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_type = 'BASE TABLE'
              AND table_name <> ?
            """;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    @Bean
    ApplicationListener<BeforeTestMethodEvent> truncateAppTablesBeforeEachTest(JdbcTemplate jdbcTemplate) {
        return event -> truncateAppTables(jdbcTemplate);
    }

    private void truncateAppTables(JdbcTemplate jdbcTemplate) {
        List<String> tables = jdbcTemplate.queryForList(SELECT_APP_TABLES, String.class, FLYWAY_HISTORY_TABLE);
        if (tables.isEmpty()) {
            return;
        }
        // Safe to concatenate: names are read from Postgres' system catalog, never user input.
        String quotedTableList = tables.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + quotedTableList + " CASCADE");
    }
}
