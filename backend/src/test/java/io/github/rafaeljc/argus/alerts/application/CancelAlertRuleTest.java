package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelAlertRuleTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RuleId RULE_ID = new RuleId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private AlertRuleRepository repository;

    private CancelAlertRule cancelAlertRule;

    @BeforeEach
    void setUp() {
        cancelAlertRule = new CancelAlertRule(repository);
    }

    @Test
    void cancel_activeRuleOwnedByUser_deletesRule() {
        AlertRule rule = new AlertRule(
                RULE_ID, USER_ID, Direction.UP, new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30), Instant.parse("2026-07-01T12:00:00Z"));
        when(repository.deleteActiveAndReturn(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        cancelAlertRule.cancel(USER_ID, RULE_ID);

        verify(repository).deleteActiveAndReturn(RULE_ID, USER_ID);
    }

    @Test
    void cancel_noActiveRuleForUser_throwsResourceNotFound() {
        when(repository.deleteActiveAndReturn(RULE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelAlertRule.cancel(USER_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_ruleOwnedByAnotherUser_throwsResourceNotFound() {
        when(repository.deleteActiveAndReturn(RULE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelAlertRule.cancel(USER_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).deleteActiveAndReturn(RULE_ID, USER_ID);
    }
}
