package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcHoldingRepository implements HoldingRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO holdings (user_id, ticker, quantity, updated_at)
            VALUES (:userId, :ticker, :quantity, :updatedAt)
            ON CONFLICT (user_id, ticker) DO UPDATE
               SET quantity   = EXCLUDED.quantity,
                   updated_at = EXCLUDED.updated_at
            """;

    private static final String DELETE_SQL = "DELETE FROM holdings WHERE user_id = :userId AND ticker = :ticker";

    private static final String FIND_SQL =
            "SELECT user_id, ticker, quantity, updated_at FROM holdings WHERE user_id = :userId AND ticker = :ticker";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcHoldingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(UserId userId, Ticker ticker, Quantity quantity, Instant now) {
        jdbc.update(UPSERT_SQL, paramsFor(userId, ticker, quantity, now));
    }

    @Override
    public void deleteIfPresent(UserId userId, Ticker ticker) {
        jdbc.update(DELETE_SQL, idParams(userId, ticker));
    }

    @Override
    public Optional<Holding> find(UserId userId, Ticker ticker) {
        List<Holding> rows = jdbc.query(FIND_SQL, idParams(userId, ticker), JdbcHoldingRepository::mapRow);
        return rows.stream().findFirst();
    }

    private static MapSqlParameterSource idParams(UserId userId, Ticker ticker) {
        return new MapSqlParameterSource().addValue("userId", userId.value()).addValue("ticker", ticker.value());
    }

    private static MapSqlParameterSource paramsFor(UserId userId, Ticker ticker, Quantity quantity, Instant now) {
        return idParams(userId, ticker)
                .addValue("quantity", quantity.value())
                .addValue("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    private static Holding mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Holding(
                new UserId(rs.getObject("user_id", UUID.class)),
                new Ticker(rs.getString("ticker")),
                new Quantity(rs.getBigDecimal("quantity")),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
