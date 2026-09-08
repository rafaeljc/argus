package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum StepStatus implements DbValued {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped");

    private static final DbValueLookup<StepStatus> LOOKUP = DbValueLookup.of(values(), "step_status");

    private final String dbValue;

    StepStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public static StepStatus fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
