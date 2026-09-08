package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum RunStatus implements DbValued {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private static final DbValueLookup<RunStatus> LOOKUP = DbValueLookup.of(values(), "run_status");

    private final String dbValue;

    RunStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static RunStatus fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
