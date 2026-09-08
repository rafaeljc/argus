package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum Exchange implements DbValued {

    NYSE("NYSE"),
    NASDAQ("NASDAQ");

    private static final DbValueLookup<Exchange> LOOKUP = DbValueLookup.of(values(), "exchange");

    private final String dbValue;

    Exchange(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static Exchange fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
