package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

    private static final RuleId RULE_ID = new RuleId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Percentage THRESHOLD = new Percentage(new BigDecimal("5.0"));
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void constructor_validInput_exposesComponents() {
        AlertRule rule = new AlertRule(RULE_ID, USER_ID, Direction.UP, THRESHOLD, WINDOW, CREATED_AT);

        assertThat(rule.id()).isEqualTo(RULE_ID);
        assertThat(rule.userId()).isEqualTo(USER_ID);
        assertThat(rule.direction()).isEqualTo(Direction.UP);
        assertThat(rule.threshold()).isEqualTo(THRESHOLD);
        assertThat(rule.window()).isEqualTo(WINDOW);
        assertThat(rule.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(null, USER_ID, Direction.UP, THRESHOLD, WINDOW, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(RULE_ID, null, Direction.UP, THRESHOLD, WINDOW, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullDirection_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(RULE_ID, USER_ID, null, THRESHOLD, WINDOW, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullThreshold_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(RULE_ID, USER_ID, Direction.UP, null, WINDOW, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullWindow_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(RULE_ID, USER_ID, Direction.UP, THRESHOLD, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AlertRule(RULE_ID, USER_ID, Direction.UP, THRESHOLD, WINDOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
