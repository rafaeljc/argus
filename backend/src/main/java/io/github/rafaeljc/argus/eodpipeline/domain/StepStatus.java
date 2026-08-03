package io.github.rafaeljc.argus.eodpipeline.domain;

public enum StepStatus {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped");

    private final String dbValue;

    StepStatus(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("StepStatus dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static StepStatus fromDbValue(String value) {
        for (StepStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown step_status: " + value);
    }
}
