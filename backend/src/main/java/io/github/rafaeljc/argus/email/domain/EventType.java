package io.github.rafaeljc.argus.email.domain;

public enum EventType {

    VERIFICATION("email.verification"),
    PASSWORD_RESET("email.password_reset"),
    DIGEST("email.digest");

    private final String dbValue;

    EventType(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("EventType dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static EventType fromDbValue(String value) {
        for (EventType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown event_type: " + value);
    }
}
