package io.github.rafaeljc.argus.transactions.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransactionRepository implements TransactionRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO transactions (id, user_id, ticker, operation, quantity, trade_date, created_at, updated_at)
            VALUES (:id, :userId, :ticker, :operation, :quantity, :tradeDate, :createdAt, :updatedAt)
            """;

    private static final String FIND_BY_ID_AND_USER_ID_SQL =
            """
            SELECT id, user_id, ticker, operation, quantity, trade_date, created_at, updated_at
            FROM transactions
            WHERE id = :id AND user_id = :userId
            """;

    private static final String DELETE_BY_ID_AND_USER_ID_SQL =
            "DELETE FROM transactions WHERE id = :id AND user_id = :userId";

    private static final String LIST_BY_USER_ID_SQL =
            """
            SELECT id, user_id, ticker, operation, quantity, trade_date, created_at, updated_at
            FROM transactions
            WHERE user_id = :userId
            ORDER BY trade_date DESC, created_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_BY_USER_ID_SQL = "SELECT count(*) FROM transactions WHERE user_id = :userId";

    private static final String FIND_LATER_SELLS_SQL =
            """
            SELECT id, user_id, ticker, operation, quantity, trade_date, created_at, updated_at
            FROM transactions
            WHERE user_id = :userId AND ticker = :ticker AND operation = 'SELL' AND trade_date > :after
            ORDER BY trade_date, created_at
            """;

    private static final String FIND_ALL_AFTER_SQL =
            """
            SELECT id, user_id, ticker, operation, quantity, trade_date, created_at, updated_at
            FROM transactions
            WHERE user_id = :userId AND ticker = :ticker AND trade_date > :after
            ORDER BY trade_date, created_at
            """;

    private static final String HOLDINGS_AS_OF_SQL =
            """
            SELECT COALESCE(SUM(CASE WHEN operation = 'BUY' THEN quantity ELSE -quantity END), 0) AS net
            FROM transactions
            WHERE user_id = :userId AND ticker = :ticker AND trade_date <= :asOf
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcTransactionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Transaction save(Transaction transaction) {
        jdbc.update(INSERT_SQL, paramsFor(transaction));
        return transaction;
    }

    @Override
    public Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId) {
        List<Transaction> rows =
                jdbc.query(FIND_BY_ID_AND_USER_ID_SQL, idParams(id, userId), JdbcTransactionRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public boolean deleteByIdAndUserId(TransactionId id, UserId userId) {
        return jdbc.update(DELETE_BY_ID_AND_USER_ID_SQL, idParams(id, userId)) > 0;
    }

    @Override
    public List<Transaction> listByUserId(UserId userId, int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbc.query(LIST_BY_USER_ID_SQL, params, JdbcTransactionRepository::mapRow);
    }

    @Override
    public int countByUserId(UserId userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId.value());
        Integer count = jdbc.queryForObject(COUNT_BY_USER_ID_SQL, params, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<Transaction> findLaterSells(UserId userId, Ticker ticker, LocalDate after) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("ticker", ticker.value())
                .addValue("after", after);
        return jdbc.query(FIND_LATER_SELLS_SQL, params, JdbcTransactionRepository::mapRow);
    }

    @Override
    public List<Transaction> findAllAfter(UserId userId, Ticker ticker, LocalDate after) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("ticker", ticker.value())
                .addValue("after", after);
        return jdbc.query(FIND_ALL_AFTER_SQL, params, JdbcTransactionRepository::mapRow);
    }

    @Override
    public BigDecimal holdingsAsOf(UserId userId, Ticker ticker, LocalDate asOf) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("ticker", ticker.value())
                .addValue("asOf", asOf);
        BigDecimal net = jdbc.queryForObject(HOLDINGS_AS_OF_SQL, params, BigDecimal.class);
        return net == null ? BigDecimal.ZERO : net;
    }

    private static MapSqlParameterSource idParams(TransactionId id, UserId userId) {
        return new MapSqlParameterSource().addValue("id", id.value()).addValue("userId", userId.value());
    }

    private static MapSqlParameterSource paramsFor(Transaction transaction) {
        return new MapSqlParameterSource()
                .addValue("id", transaction.id().value())
                .addValue("userId", transaction.userId().value())
                .addValue("ticker", transaction.ticker().value())
                .addValue("operation", transaction.operation().name())
                .addValue("quantity", transaction.quantity().value())
                .addValue("tradeDate", transaction.tradeDate())
                .addValue("createdAt", OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
                .addValue("updatedAt", OffsetDateTime.ofInstant(transaction.updatedAt(), ZoneOffset.UTC));
    }

    private static Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Transaction(
                new TransactionId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                new Ticker(rs.getString("ticker")),
                Operation.valueOf(rs.getString("operation")),
                new Quantity(rs.getBigDecimal("quantity")),
                rs.getObject("trade_date", LocalDate.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
