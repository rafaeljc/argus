package io.github.rafaeljc.argus.support.containers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainer.class)
class PostgresContainerIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RepeatedTest(2)
    void eachRun_startsWithAllAppTablesEmpty_acrossUnrelatedTables() {
        assertThat(countRows("users")).isZero();
        assertThat(countRows("symbols")).isZero();

        insertUser();
        insertSymbol();

        assertThat(countRows("users")).isEqualTo(1);
        assertThat(countRows("symbols")).isEqualTo(1);
    }

    private Integer countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void insertUser() {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                UUID.randomUUID(),
                "truncate-contract@example.com",
                "x");
    }

    private void insertSymbol() {
        jdbcTemplate.update("INSERT INTO symbols (ticker, exchange) VALUES (?, ?)", "AAPL", "NASDAQ");
    }
}
