package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPriceHistoryRepository implements PriceHistoryRepository {

    // 1000 rows per batch keeps each round-trip well under Postgres' 65,535 parameter limit
    // (6 params/row × 1000 = 6,000) while amortising network overhead across a full backfill
    // (~1,250 rows/ticker for the 5-year daily window).
    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL =
            """
            INSERT INTO price_history
                (ticker, trade_date, close_price, is_split_adjusted, created_at, updated_at)
            VALUES
                (:ticker, :tradeDate, :closePrice, :isSplitAdjusted, now(), now())
            ON CONFLICT (ticker, trade_date) DO UPDATE
               SET close_price       = EXCLUDED.close_price,
                   is_split_adjusted = EXCLUDED.is_split_adjusted,
                   updated_at        = now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPriceHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int upsertBatch(List<PriceHistory> prices) {
        if (prices.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int fromIndex = 0; fromIndex < prices.size(); fromIndex += BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_SIZE, prices.size());
            total += executeBatch(prices.subList(fromIndex, toIndex));
        }
        return total;
    }

    private int executeBatch(List<PriceHistory> chunk) {
        SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
        for (int i = 0; i < chunk.size(); i++) {
            batch[i] = toParameters(chunk.get(i));
        }
        int[] rowsPerStatement = jdbc.batchUpdate(UPSERT_SQL, batch);
        int sum = 0;
        for (int rows : rowsPerStatement) {
            sum += rows;
        }
        return sum;
    }

    private static SqlParameterSource toParameters(PriceHistory price) {
        return new MapSqlParameterSource()
                .addValue("ticker", price.ticker().value())
                .addValue("tradeDate", price.tradeDate())
                .addValue("closePrice", price.closePrice())
                .addValue("isSplitAdjusted", price.isSplitAdjusted());
    }
}
