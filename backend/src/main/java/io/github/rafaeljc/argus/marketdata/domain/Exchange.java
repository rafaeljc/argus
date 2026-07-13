package io.github.rafaeljc.argus.marketdata.domain;

public enum Exchange {

    NYSE("NYSE"),
    NASDAQ("NASDAQ");

    private final String dbValue;

    Exchange(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("Exchange dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Exchange fromDbValue(String value) {
        for (Exchange exchange : values()) {
            if (exchange.dbValue.equals(value)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("unknown exchange: " + value);
    }
}
