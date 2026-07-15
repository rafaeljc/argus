package io.github.rafaeljc.argus.marketdata.application.port;

import java.time.LocalDate;

// US-market trading calendar. Both methods take an explicit LocalDate so callers stay explicit about the
// as-of date (Clock lives at the caller). v1 stub covers weekends + US federal holidays with NYSE observed-shift
// rules; the real vendor calendar adapter ships in Phase 10.
public interface MarketCalendar {

    boolean isTradingDay(LocalDate date);

    LocalDate mostRecentTradingDayOnOrBefore(LocalDate date);
}
