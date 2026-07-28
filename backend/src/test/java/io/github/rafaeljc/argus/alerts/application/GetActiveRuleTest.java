package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class GetActiveRuleTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RuleId RULE_ID = new RuleId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private AlertRuleRepository repository;

    private GetActiveRule getActiveRule;

    @BeforeEach
    void setUp() {
        getActiveRule = new GetActiveRule(repository);
    }

    @Test
    void get_found_returnsRule() {
        AlertRule rule = new AlertRule(
                RULE_ID, USER_ID, Direction.UP, new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30), NOW);
        when(repository.findActiveByIdAndUser(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        AlertRule result = getActiveRule.get(USER_ID, RULE_ID);

        assertThat(result).isEqualTo(rule);
    }

    @Test
    void get_missing_throwsResourceNotFound() {
        when(repository.findActiveByIdAndUser(RULE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getActiveRule.get(USER_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
