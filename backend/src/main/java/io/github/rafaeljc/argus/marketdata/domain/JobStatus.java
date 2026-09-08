package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum JobStatus implements DbValued {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed");

    private static final DbValueLookup<JobStatus> LOOKUP = DbValueLookup.of(values(), "job_status");

    private final String dbValue;

    JobStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static JobStatus fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
