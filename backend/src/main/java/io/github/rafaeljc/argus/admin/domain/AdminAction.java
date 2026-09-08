package io.github.rafaeljc.argus.admin.domain;

import io.github.rafaeljc.argus.common.domain.DbValueLookup;
import io.github.rafaeljc.argus.common.domain.DbValued;

public enum AdminAction implements DbValued {

    SUSPEND("SUSPEND"),
    UNSUSPEND("UNSUSPEND"),
    DELETE("DELETE"),
    EOD_RUN("EOD_RUN"),
    EOD_STEP_RERUN("EOD_STEP_RERUN");

    private static final DbValueLookup<AdminAction> LOOKUP = DbValueLookup.of(values(), "action");

    private final String dbValue;

    AdminAction(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }

    public boolean requiresTargetUser() {
        return equals(SUSPEND) || equals(UNSUSPEND) || equals(DELETE);
    }

    public static AdminAction fromDbValue(String value) {
        return LOOKUP.get(value);
    }
}
