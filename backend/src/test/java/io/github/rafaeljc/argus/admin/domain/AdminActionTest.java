package io.github.rafaeljc.argus.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminActionTest {

    @ParameterizedTest
    @CsvSource({
            "SUSPEND,SUSPEND",
            "UNSUSPEND,UNSUSPEND",
            "DELETE,DELETE",
            "EOD_RUN,EOD_RUN",
            "EOD_STEP_RERUN,EOD_STEP_RERUN"
    })
    void dbValue_matchesSchemaCheckConstraint(AdminAction action, String expected) {
        assertThat(action.dbValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "SUSPEND,SUSPEND",
            "UNSUSPEND,UNSUSPEND",
            "DELETE,DELETE",
            "EOD_RUN,EOD_RUN",
            "EOD_STEP_RERUN,EOD_STEP_RERUN"
    })
    void fromDbValue_knownValue_returnsMatchingConstant(String dbValue, AdminAction expected) {
        assertThat(AdminAction.fromDbValue(dbValue)).isEqualTo(expected);
    }

    @Test
    void fromDbValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> AdminAction.fromDbValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDbValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> AdminAction.fromDbValue("BAN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BAN");
    }

    @Test
    void fromDbValue_isCaseSensitive() {
        assertThatThrownBy(() -> AdminAction.fromDbValue("suspend"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"SUSPEND", "UNSUSPEND", "DELETE"})
    void requiresTargetUser_userTargetedActions_returnsTrue(AdminAction action) {
        assertThat(action.requiresTargetUser()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"EOD_RUN", "EOD_STEP_RERUN"})
    void requiresTargetUser_pipelineActions_returnsFalse(AdminAction action) {
        assertThat(action.requiresTargetUser()).isFalse();
    }
}
