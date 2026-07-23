package io.github.rafaeljc.argus.portfolio.application;

import java.time.LocalDate;
import java.time.Period;

public enum SnapshotRange {
    M1("1m", Period.ofMonths(1)),
    M3("3m", Period.ofMonths(3)),
    M6("6m", Period.ofMonths(6)),
    Y1("1y", Period.ofYears(1)),
    Y3("3y", Period.ofYears(3)),
    Y5("5y", Period.ofYears(5));

    private final String wireValue;
    private final Period period;

    SnapshotRange(String wireValue, Period period) {
        this.wireValue = wireValue;
        this.period = period;
    }

    public static SnapshotRange fromWire(String wireValue) {
        for (SnapshotRange range : values()) {
            if (range.wireValue.equalsIgnoreCase(wireValue)) {
                return range;
            }
        }
        throw new InvalidSnapshotRangeException(wireValue);
    }

    public LocalDate from(LocalDate anchor) {
        return anchor.minus(period);
    }
}
