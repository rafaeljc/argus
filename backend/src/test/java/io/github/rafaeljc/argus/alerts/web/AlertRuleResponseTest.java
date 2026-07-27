package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertRuleResponseTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T12:34:56Z");

    @Test
    void from_projectsContractFields() {
        AlertRule rule = new AlertRule(
                new RuleId(ID),
                new UserId(UuidCreator.getTimeOrderedEpoch()),
                Direction.DOWN,
                new Percentage(new BigDecimal("7.5")),
                new AlertLookbackWindow(90),
                CREATED_AT);

        AlertRuleResponse response = AlertRuleResponse.from(rule);

        assertThat(response.id()).isEqualTo(ID);
        assertThat(response.direction()).isEqualTo("DOWN");
        assertThat(response.threshold()).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(response.windowDays()).isEqualTo(90);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }
}
