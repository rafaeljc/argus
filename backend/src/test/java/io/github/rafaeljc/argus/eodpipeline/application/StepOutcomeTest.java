package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StepOutcomeTest {

    @Test
    void success_returnsSucceededOutcomeWithNoErrorMessage() {
        StepOutcome outcome = StepOutcome.success();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.errorMessage()).isNull();
    }

    @Test
    void failure_returnsUnsucceededOutcomeCarryingTheMessage() {
        StepOutcome outcome = StepOutcome.failure("vendor 503");

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.errorMessage()).isEqualTo("vendor 503");
    }

    @Test
    void construction_failedWithNullMessage_throws() {
        assertThatThrownBy(() -> new StepOutcome(false, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construction_failedWithBlankMessage_throws() {
        assertThatThrownBy(() -> new StepOutcome(false, "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construction_succeededWithMessage_isAllowed() {
        StepOutcome outcome = new StepOutcome(true, "ignored but present");

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.errorMessage()).isEqualTo("ignored but present");
    }
}
