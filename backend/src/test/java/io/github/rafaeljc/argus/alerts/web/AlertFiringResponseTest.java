package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertFiringResponseTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID RULE_UUID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final Instant FIRED_AT = Instant.parse("2026-06-01T12:34:56Z");

    @Test
    void from_projectsContractFields() {
        AlertFiring firing = new AlertFiring(
                new FiringId(ID),
                new UserId(UuidCreator.getTimeOrderedEpoch()),
                new RuleId(RULE_UUID),
                Direction.DOWN,
                new Percentage(new BigDecimal("7.5")),
                new AlertLookbackWindow(90),
                FIRED_AT,
                new Money(new BigDecimal("1000.00")),
                new Money(new BigDecimal("1123.45")),
                new BigDecimal("12.35"),
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-06-01"));

        AlertFiringResponse response = AlertFiringResponse.from(firing);

        assertThat(response.id()).isEqualTo(ID);
        assertThat(response.ruleId()).isEqualTo(RULE_UUID);
        assertThat(response.direction()).isEqualTo("DOWN");
        assertThat(response.threshold()).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(response.windowDays()).isEqualTo(90);
        assertThat(response.firedAt()).isEqualTo(FIRED_AT);
        assertThat(response.portfolioValueStart()).isEqualTo("1000.00");
        assertThat(response.portfolioValueEnd()).isEqualTo("1123.45");
        assertThat(response.percentChange()).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(response.windowStartDate()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(response.windowEndDate()).isEqualTo(LocalDate.parse("2026-06-01"));
    }
}
