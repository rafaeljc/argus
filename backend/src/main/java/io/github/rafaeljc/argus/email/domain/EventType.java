package io.github.rafaeljc.argus.email.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum EventType implements DbValued {

    VERIFICATION("email.verification"),
    PASSWORD_RESET("email.password_reset"),
    DIGEST("email.digest");

    private static final DbValueLookup<EventType> LOOKUP = DbValueLookup.of(values(), "event_type");

    private final String dbValue;

    EventType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static EventType fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
