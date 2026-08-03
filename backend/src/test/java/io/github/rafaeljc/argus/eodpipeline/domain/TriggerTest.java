package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TriggerTest {

    @ParameterizedTest
    @CsvSource({
            "CRON,cron",
            "ADMIN,admin"
    })
    void dbValue_matchesSchemaCheckConstraint(Trigger trigger, String expected) {
        assertThat(trigger.dbValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "cron,CRON",
            "admin,ADMIN"
    })
    void fromDbValue_knownValue_returnsMatchingConstant(String dbValue, Trigger expected) {
        assertThat(Trigger.fromDbValue(dbValue)).isEqualTo(expected);
    }

    @Test
    void fromDbValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> Trigger.fromDbValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDbValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> Trigger.fromDbValue("scheduler"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduler");
    }

    @Test
    void fromDbValue_isCaseSensitive() {
        assertThatThrownBy(() -> Trigger.fromDbValue("CRON"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
