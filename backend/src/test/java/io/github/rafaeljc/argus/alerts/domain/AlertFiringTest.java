package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AlertFiringTest {

    private static final FiringId FIRING_ID = new FiringId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RuleId RULE_ID = new RuleId(UuidCreator.getTimeOrderedEpoch());
    private static final Percentage THRESHOLD = new Percentage(new BigDecimal("5.0"));
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);
    private static final Instant FIRED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Money VALUE_START = new Money(new BigDecimal("1000.00"));
    private static final Money VALUE_END = new Money(new BigDecimal("1100.00"));
    private static final LocalDate WINDOW_START = LocalDate.parse("2026-06-01");
    private static final LocalDate WINDOW_END = LocalDate.parse("2026-07-01");

    @Test
    void constructor_validInput_exposesComponents() {
        AlertFiring firing = new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END);

        assertThat(firing.id()).isEqualTo(FIRING_ID);
        assertThat(firing.userId()).isEqualTo(USER_ID);
        assertThat(firing.ruleId()).isEqualTo(RULE_ID);
        assertThat(firing.direction()).isEqualTo(Direction.UP);
        assertThat(firing.threshold()).isEqualTo(THRESHOLD);
        assertThat(firing.window()).isEqualTo(WINDOW);
        assertThat(firing.firedAt()).isEqualTo(FIRED_AT);
        assertThat(firing.portfolioValueStart()).isEqualTo(VALUE_START);
        assertThat(firing.portfolioValueEnd()).isEqualTo(VALUE_END);
        assertThat(firing.percentChange()).isEqualByComparingTo("10.00");
        assertThat(firing.windowStartDate()).isEqualTo(WINDOW_START);
        assertThat(firing.windowEndDate()).isEqualTo(WINDOW_END);
    }

    @Test
    void constructor_negativePercentChange_isAccepted() {
        AlertFiring firing = new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.DOWN, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("-7.50"), WINDOW_START, WINDOW_END);

        assertThat(firing.percentChange()).isEqualByComparingTo("-7.50");
    }

    @Test
    void constructor_percentChangeScaleExceedsTwo_normalizesToScaleTwo() {
        AlertFiring firing = new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.005"), WINDOW_START, WINDOW_END);

        assertThat(firing.percentChange().scale()).isEqualTo(2);
    }

    @Test
    void constructor_windowEndBeforeWindowStart_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_END, WINDOW_START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_windowEndEqualsWindowStart_isAccepted() {
        assertThat(new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, new AlertLookbackWindow(1), FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_START)
                .windowStartDate()).isEqualTo(WINDOW_START);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                null, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, null, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullRuleId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, null, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullDirection_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, null, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullThreshold_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, null, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullWindow_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, null, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullFiredAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, null,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullPortfolioValueStart_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                null, VALUE_END, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullPortfolioValueEnd_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, null, new BigDecimal("10.00"), WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullPercentChange_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, null, WINDOW_START, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullWindowStartDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), null, WINDOW_END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullWindowEndDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertFiring(
                FIRING_ID, USER_ID, RULE_ID, Direction.UP, THRESHOLD, WINDOW, FIRED_AT,
                VALUE_START, VALUE_END, new BigDecimal("10.00"), WINDOW_START, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
