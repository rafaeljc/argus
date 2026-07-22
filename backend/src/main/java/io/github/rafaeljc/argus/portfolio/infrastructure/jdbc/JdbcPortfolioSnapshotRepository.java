package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPortfolioSnapshotRepository implements PortfolioSnapshotRepository {

    private static final String INSERT_IF_ABSENT_SQL =
            """
            INSERT INTO portfolio_snapshots (user_id, snapshot_date, total_value)
            VALUES (:userId, :snapshotDate, :totalValue)
            ON CONFLICT (user_id, snapshot_date) DO NOTHING
            """;

    private static final String FIND_BY_USER_AND_DATE_SQL =
            """
            SELECT user_id, snapshot_date, total_value
              FROM portfolio_snapshots
             WHERE user_id = :userId
               AND snapshot_date = :snapshotDate
            """;

    private static final String LIST_BY_USER_AND_RANGE_SQL =
            """
            SELECT user_id, snapshot_date, total_value
              FROM portfolio_snapshots
             WHERE user_id = :userId
               AND (CAST(:from AS DATE) IS NULL OR snapshot_date >= CAST(:from AS DATE))
               AND (CAST(:to AS DATE) IS NULL OR snapshot_date <= CAST(:to AS DATE))
             ORDER BY snapshot_date DESC
             LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPortfolioSnapshotRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(PortfolioSnapshot snapshot) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", snapshot.userId().value())
                .addValue("snapshotDate", snapshot.snapshotDate())
                .addValue("totalValue", snapshot.totalValue().value());
        jdbc.update(INSERT_IF_ABSENT_SQL, params);
    }

    @Override
    public Optional<PortfolioSnapshot> findByUserAndDate(UserId userId, LocalDate snapshotDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("snapshotDate", snapshotDate);
        List<PortfolioSnapshot> rows =
                jdbc.query(FIND_BY_USER_AND_DATE_SQL, params, JdbcPortfolioSnapshotRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<PortfolioSnapshot> listByUserAndRange(
            UserId userId, LocalDate from, LocalDate to, int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("from", from)
                .addValue("to", to)
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbc.query(LIST_BY_USER_AND_RANGE_SQL, params, JdbcPortfolioSnapshotRepository::mapRow);
    }

    private static PortfolioSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PortfolioSnapshot(
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getObject("snapshot_date", LocalDate.class),
                new Money(rs.getBigDecimal("total_value")));
    }
}
