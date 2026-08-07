package io.github.rafaeljc.argus.admin.domain;

public enum AdminAction {

    SUSPEND("SUSPEND"),
    UNSUSPEND("UNSUSPEND"),
    DELETE("DELETE"),
    EOD_RUN("EOD_RUN"),
    EOD_STEP_RERUN("EOD_STEP_RERUN");

    private final String dbValue;

    AdminAction(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            throw new IllegalArgumentException("AdminAction dbValue must not be blank");
        }
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public boolean requiresTargetUser() {
        return equals(SUSPEND) || equals(UNSUSPEND) || equals(DELETE);
    }

    public static AdminAction fromDbValue(String value) {
        for (AdminAction action : values()) {
            if (action.dbValue.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("unknown action: " + value);
    }
}
