package io.github.rafaeljc.argus.eodpipeline.domain;

public enum RunStatus {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private final String dbValue;

    RunStatus(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("RunStatus dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RunStatus fromDbValue(String value) {
        for (RunStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown run_status: " + value);
    }
}
