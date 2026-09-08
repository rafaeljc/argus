package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum Trigger implements DbValued {

    CRON("cron"),
    ADMIN("admin");

    private static final DbValueLookup<Trigger> LOOKUP = DbValueLookup.of(values(), "trigger");

    private final String dbValue;

    Trigger(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static Trigger fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
