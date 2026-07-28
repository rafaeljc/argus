package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RuleId RULE_ID = new RuleId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private CreateAlertRule createAlertRule;

    @Mock
    private CancelAlertRule cancelAlertRule;

    @Mock
    private ListActiveRules listActiveRules;

    @Mock
    private GetActiveRule getActiveRule;

    @Mock
    private ListFirings listFirings;

    private AlertService service;

    @BeforeEach
    void setUp() {
        service = new AlertService(createAlertRule, cancelAlertRule, listActiveRules, getActiveRule, listFirings);
    }

    @Test
    void create_delegatesToCreateAlertRuleAndReturnsItsResult() {
        AlertRule expected = new AlertRule(
                RULE_ID, USER_ID, Direction.UP, new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30), NOW);
        when(createAlertRule.create(USER_ID, Direction.UP, expected.threshold(), expected.window()))
                .thenReturn(expected);

        AlertRule result = service.create(USER_ID, Direction.UP, expected.threshold(), expected.window());

        assertThat(result).isEqualTo(expected);
        verify(createAlertRule).create(USER_ID, Direction.UP, expected.threshold(), expected.window());
    }

    @Test
    void cancel_delegatesToCancelAlertRule() {
        service.cancel(USER_ID, RULE_ID);

        verify(cancelAlertRule).cancel(USER_ID, RULE_ID);
    }

    @Test
    void listRules_delegatesToListActiveRulesAndReturnsItsResult() {
        AlertRule rule = new AlertRule(
                RULE_ID, USER_ID, Direction.UP, new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30), NOW);
        PageResult<AlertRule> expected = new PageResult<>(List.of(rule), 1, 1, 50);
        when(listActiveRules.list(USER_ID, 1, 50)).thenReturn(expected);

        PageResult<AlertRule> result = service.listRules(USER_ID, 1, 50);

        assertThat(result).isEqualTo(expected);
        verify(listActiveRules).list(USER_ID, 1, 50);
    }

    @Test
    void getRule_delegatesToGetActiveRuleAndReturnsItsResult() {
        AlertRule expected = new AlertRule(
                RULE_ID, USER_ID, Direction.UP, new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30), NOW);
        when(getActiveRule.get(USER_ID, RULE_ID)).thenReturn(expected);

        AlertRule result = service.getRule(USER_ID, RULE_ID);

        assertThat(result).isEqualTo(expected);
        verify(getActiveRule).get(USER_ID, RULE_ID);
    }

    @Test
    void listFirings_delegatesToListFiringsAndReturnsItsResult() {
        AlertFiring firing = new AlertFiring(
                new FiringId(UuidCreator.getTimeOrderedEpoch()), USER_ID, RULE_ID, Direction.UP,
                new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30), NOW,
                new Money(new BigDecimal("1000.00")), new Money(new BigDecimal("1050.00")),
                new BigDecimal("5.00"), LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01"));
        PageResult<AlertFiring> expected = new PageResult<>(List.of(firing), 1, 1, 50);
        when(listFirings.list(USER_ID, 1, 50)).thenReturn(expected);

        PageResult<AlertFiring> result = service.listFirings(USER_ID, 1, 50);

        assertThat(result).isEqualTo(expected);
        verify(listFirings).list(USER_ID, 1, 50);
    }
}
