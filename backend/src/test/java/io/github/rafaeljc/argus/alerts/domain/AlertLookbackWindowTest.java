package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlertLookbackWindowTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 30, 90, 365, 1095, 1825})
    void constructor_allowedDays_isAcceptedAndExposed(int days) {
        assertThatCode(() -> new AlertLookbackWindow(days)).doesNotThrowAnyException();
        assertThat(new AlertLookbackWindow(days).days()).isEqualTo(days);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 14, 60, 180, 730, 2000, -1})
    void constructor_daysNotInAllowedSet_throwsIllegalArgument(int days) {
        assertThatThrownBy(() -> new AlertLookbackWindow(days))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
