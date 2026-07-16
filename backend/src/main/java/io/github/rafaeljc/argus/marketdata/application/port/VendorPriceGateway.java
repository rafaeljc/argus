package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface VendorPriceGateway {

    Set<Symbol> fetchSymbolUniverse();

    List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end);
}
