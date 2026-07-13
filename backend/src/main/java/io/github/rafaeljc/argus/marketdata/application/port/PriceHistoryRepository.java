package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import java.util.List;

public interface PriceHistoryRepository {

    // Bulk upsert against (ticker, trade_date). Returns the sum of per-row counts reported by the driver
    // across all batches. With pgjdbc defaults (reWriteBatchedInserts=false) each INSERT and each
    // ON CONFLICT UPDATE reports 1, so the total equals prices.size() on success. If a driver returns
    // Statement.SUCCESS_NO_INFO (-2) per row (e.g. under reWriteBatchedInserts=true), callers must not
    // treat the return value as an accurate row count — it's advisory only.
    int upsertBatch(List<PriceHistory> prices);
}
