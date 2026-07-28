package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListActiveRulesTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private AlertRuleRepository repository;

    private ListActiveRules listActiveRules;

    @BeforeEach
    void setUp() {
        listActiveRules = new ListActiveRules(repository);
    }

    @Test
    void list_delegatesPageAndPerPageToRepositoryAndReturnsItemsWithTotal() {
        AlertRule rule = new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()), USER_ID, Direction.UP,
                new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30), NOW);
        when(repository.listActiveByUserOrderedByCreatedAtDesc(USER_ID, 2, 25)).thenReturn(List.of(rule));
        when(repository.countActiveByUser(USER_ID)).thenReturn(30);

        PageResult<AlertRule> result = listActiveRules.list(USER_ID, 2, 25);

        assertThat(result.items()).containsExactly(rule);
        assertThat(result.total()).isEqualTo(30);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.perPage()).isEqualTo(25);
    }

    @Test
    void list_noRules_returnsEmptyPageWithZeroTotal() {
        when(repository.listActiveByUserOrderedByCreatedAtDesc(USER_ID, 1, 50)).thenReturn(List.of());
        when(repository.countActiveByUser(USER_ID)).thenReturn(0);

        PageResult<AlertRule> result = listActiveRules.list(USER_ID, 1, 50);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
