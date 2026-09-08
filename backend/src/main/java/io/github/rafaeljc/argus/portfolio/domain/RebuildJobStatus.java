package io.github.rafaeljc.argus.portfolio.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum RebuildJobStatus implements DbValued {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed");

    private static final DbValueLookup<RebuildJobStatus> LOOKUP =
            DbValueLookup.of(values(), "rebuild_job_status");

    private final String dbValue;

    RebuildJobStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static RebuildJobStatus fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
