package io.github.rafaeljc.argus.eodpipeline.domain;

public enum Trigger {

    CRON("cron"),
    ADMIN("admin");

    private final String dbValue;

    Trigger(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("Trigger dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Trigger fromDbValue(String value) {
        for (Trigger trigger : values()) {
            if (trigger.dbValue.equals(value)) {
                return trigger;
            }
        }
        throw new IllegalArgumentException("unknown trigger: " + value);
    }
}
