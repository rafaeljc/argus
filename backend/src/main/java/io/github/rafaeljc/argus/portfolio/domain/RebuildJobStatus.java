package io.github.rafaeljc.argus.portfolio.domain;

public enum RebuildJobStatus {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String dbValue;

    RebuildJobStatus(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("RebuildJobStatus dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RebuildJobStatus fromDbValue(String value) {
        for (RebuildJobStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown rebuild_job_status: " + value);
    }
}
