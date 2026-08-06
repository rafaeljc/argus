package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.alerts.domain.DuplicateAlertRuleException;
import io.github.rafaeljc.argus.alerts.domain.TooManyAlertRulesException;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateAlertRuleTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Direction DIRECTION = Direction.UP;
    private static final Percentage THRESHOLD = new Percentage(new BigDecimal("5.0"));
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);

    @Mock
    private AlertRuleRepository repository;

    @Mock
    private TransactionalMutationLock lock;

    private FixedClock clock;
    private CreateAlertRule createAlertRule;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        createAlertRule = new CreateAlertRule(repository, lock, clock);
    }

    @Test
    void create_underLimit_insertsRuleWithClockTimestamp() {
        when(repository.countActiveByUser(USER_ID)).thenReturn(5);
        when(repository.insert(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRule created = createAlertRule.create(USER_ID, DIRECTION, THRESHOLD, WINDOW);

        assertThat(created.userId()).isEqualTo(USER_ID);
        assertThat(created.direction()).isEqualTo(DIRECTION);
        assertThat(created.threshold()).isEqualTo(THRESHOLD);
        assertThat(created.window()).isEqualTo(WINDOW);
        assertThat(created.createdAt()).isEqualTo(FIXED_NOW);
        verify(repository).insert(created);
    }

    @Test
    void create_nineteenActiveRules_insertsTwentiethRule() {
        when(repository.countActiveByUser(USER_ID)).thenReturn(19);
        when(repository.insert(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        createAlertRule.create(USER_ID, DIRECTION, THRESHOLD, WINDOW);

        verify(repository).insert(any(AlertRule.class));
    }

    @Test
    void create_twentyActiveRules_throwsTooManyAlertRulesAndDoesNotInsert() {
        when(repository.countActiveByUser(USER_ID)).thenReturn(20);

        assertThatThrownBy(() -> createAlertRule.create(USER_ID, DIRECTION, THRESHOLD, WINDOW))
                .isInstanceOfSatisfying(TooManyAlertRulesException.class, ex -> assertThat(ex.limit()).isEqualTo(20));

        verify(repository, never()).insert(any());
    }

    @Test
    void create_duplicateSignature_propagatesDuplicateAlertRuleException() {
        when(repository.countActiveByUser(USER_ID)).thenReturn(0);
        when(repository.insert(any(AlertRule.class)))
                .thenThrow(new DuplicateAlertRuleException(DIRECTION, THRESHOLD, WINDOW));

        assertThatThrownBy(() -> createAlertRule.create(USER_ID, DIRECTION, THRESHOLD, WINDOW))
                .isInstanceOf(DuplicateAlertRuleException.class);
    }

    @Test
    void create_locksBeforeCountingActiveRules() {
        when(repository.countActiveByUser(USER_ID)).thenReturn(0);
        when(repository.insert(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        createAlertRule.create(USER_ID, DIRECTION, THRESHOLD, WINDOW);

        InOrder order = Mockito.inOrder(lock, repository);
        order.verify(lock).acquireResourceById("alert-rule", USER_ID.value());
        order.verify(repository).countActiveByUser(USER_ID);
    }
}
