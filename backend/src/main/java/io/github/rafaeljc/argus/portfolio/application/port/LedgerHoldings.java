package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LedgerHoldings {

    record NetQuantityPoint(Ticker ticker, LocalDate effectiveFrom, BigDecimal netQuantity) {

        public NetQuantityPoint {
            if (ticker == null) {
                throw new IllegalArgumentException("NetQuantityPoint ticker must not be null");
            }
            if (effectiveFrom == null) {
                throw new IllegalArgumentException("NetQuantityPoint effectiveFrom must not be null");
            }
            if (netQuantity == null) {
                throw new IllegalArgumentException("NetQuantityPoint netQuantity must not be null");
            }
        }
    }

    List<NetQuantityPoint> timeline(UserId userId, LocalDate through);
}
