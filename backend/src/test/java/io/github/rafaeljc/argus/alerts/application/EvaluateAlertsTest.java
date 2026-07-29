package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import io.github.rafaeljc.argus.portfolio.application.GetSnapshot;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EvaluateAlertsTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final LocalDate RUN_DATE = LocalDate.parse("2026-07-01");
    private static final LocalDate WINDOW_START = LocalDate.parse("2026-05-29");
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);
    private static final String USER_EMAIL = "user@example.com";

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private AlertFiringRepository firingRepository;

    @Mock
    private GetSnapshot getSnapshot;

    @Mock
    private MarketCalendar marketCalendar;

    @Mock
    private EmailService emailService;

    @Mock
    private UserService userService;

    @Mock
    private TransactionalMutationLock lock;

    private FixedClock clock;
    private EvaluateAlerts evaluateAlerts;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        evaluateAlerts = new EvaluateAlerts(
                ruleRepository,
                firingRepository,
                getSnapshot,
                marketCalendar,
                emailService,
                userService,
                lock,
                clock,
                new ObjectMapper());
    }

    @Test
    void forUser_upThresholdMet_deletesRuleInsertsFiringAndEnqueuesDigest() {
        AlertRule rule = upRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10600.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        Money start = new Money(new BigDecimal("10000.00"));
        when(getSnapshot.at(USER_ID, WINDOW_START))
                .thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, WINDOW_START, start)));
        when(ruleRepository.deleteActiveAndReturn(rule.id(), USER_ID)).thenReturn(Optional.of(rule));
        when(firingRepository.insert(any(AlertFiring.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubUser();

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        ArgumentCaptor<AlertFiring> firingCaptor = ArgumentCaptor.forClass(AlertFiring.class);
        verify(firingRepository).insert(firingCaptor.capture());
        AlertFiring firing = firingCaptor.getValue();
        assertThat(firing.userId()).isEqualTo(USER_ID);
        assertThat(firing.ruleId()).isEqualTo(rule.id());
        assertThat(firing.direction()).isEqualTo(Direction.UP);
        assertThat(firing.threshold()).isEqualTo(rule.threshold());
        assertThat(firing.window()).isEqualTo(WINDOW);
        assertThat(firing.firedAt()).isEqualTo(FIXED_NOW);
        assertThat(firing.portfolioValueStart()).isEqualTo(start);
        assertThat(firing.portfolioValueEnd()).isEqualTo(end);
        assertThat(firing.percentChange()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(firing.windowStartDate()).isEqualTo(WINDOW_START);
        assertThat(firing.windowEndDate()).isEqualTo(RUN_DATE);

        verify(emailService)
                .enqueue(
                        eq(EventType.DIGEST),
                        eq(USER_ID.value()),
                        any(String.class),
                        eq("digest:" + USER_ID.value() + ":" + RUN_DATE));
    }

    @Test
    void forUser_downThresholdMet_fires() {
        AlertRule rule = downRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("9400.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        Money start = new Money(new BigDecimal("10000.00"));
        when(getSnapshot.at(USER_ID, WINDOW_START))
                .thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, WINDOW_START, start)));
        when(ruleRepository.deleteActiveAndReturn(rule.id(), USER_ID)).thenReturn(Optional.of(rule));
        when(firingRepository.insert(any(AlertFiring.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubUser();

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        ArgumentCaptor<AlertFiring> firingCaptor = ArgumentCaptor.forClass(AlertFiring.class);
        verify(firingRepository).insert(firingCaptor.capture());
        assertThat(firingCaptor.getValue().percentChange()).isEqualByComparingTo(new BigDecimal("-6.00"));
    }

    @Test
    void forUser_belowThreshold_doesNotFire() {
        AlertRule rule = upRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10200.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        Money start = new Money(new BigDecimal("10000.00"));
        when(getSnapshot.at(USER_ID, WINDOW_START))
                .thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, WINDOW_START, start)));

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        verify(ruleRepository, never()).deleteActiveAndReturn(any(), any());
        verify(firingRepository, never()).insert(any());
        verify(emailService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void forUser_missingEndSnapshot_returnsWithoutFiringOrDigest() {
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.empty());

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        verify(ruleRepository, never()).listAllActiveByUser(any());
        verify(firingRepository, never()).insert(any());
        verify(emailService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void forUser_missingStartSnapshot_skipsRule() {
        AlertRule rule = upRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10600.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        when(getSnapshot.at(USER_ID, WINDOW_START)).thenReturn(Optional.empty());

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        verify(ruleRepository, never()).deleteActiveAndReturn(any(), any());
        verify(firingRepository, never()).insert(any());
        verify(emailService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void forUser_zeroStartValue_skipsRule() {
        AlertRule rule = upRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10600.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        Money start = new Money(new BigDecimal("0.00"));
        when(getSnapshot.at(USER_ID, WINDOW_START))
                .thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, WINDOW_START, start)));

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        verify(ruleRepository, never()).deleteActiveAndReturn(any(), any());
        verify(firingRepository, never()).insert(any());
        verify(emailService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void forUser_deleteReturnsEmpty_doesNotInsertFiringOrEnqueueDigest() {
        AlertRule rule = upRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10600.00"));
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, RUN_DATE, end)));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(rule));
        stubWindow();
        Money start = new Money(new BigDecimal("10000.00"));
        when(getSnapshot.at(USER_ID, WINDOW_START))
                .thenReturn(Optional.of(new PortfolioSnapshot(USER_ID, WINDOW_START, start)));
        when(ruleRepository.deleteActiveAndReturn(rule.id(), USER_ID)).thenReturn(Optional.empty());

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        verify(firingRepository, never()).insert(any());
        verify(emailService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void forUser_multipleFirings_enqueuesSingleDigestWithAllLines() {
        AlertRule ruleA = upRule(new BigDecimal("5.0"));
        AlertRule ruleB = downRule(new BigDecimal("5.0"));
        Money end = new Money(new BigDecimal("10600.00"));
        PortfolioSnapshot endSnapshot = new PortfolioSnapshot(USER_ID, RUN_DATE, end);
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.of(endSnapshot));
        when(ruleRepository.listAllActiveByUser(USER_ID)).thenReturn(List.of(ruleA, ruleB));
        stubWindow();
        Money start = new Money(new BigDecimal("10000.00"));
        PortfolioSnapshot startSnapshot = new PortfolioSnapshot(USER_ID, WINDOW_START, start);
        when(getSnapshot.at(USER_ID, WINDOW_START)).thenReturn(Optional.of(startSnapshot));
        when(ruleRepository.deleteActiveAndReturn(ruleA.id(), USER_ID)).thenReturn(Optional.of(ruleA));
        when(firingRepository.insert(any(AlertFiring.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubUser();

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        // ruleA (UP, 6.00 >= 5.0) fires. ruleB (DOWN, threshold 5.0) does not — 6.00 is not <= -5.0 —
        // so its deleteActiveAndReturn is never called (no stub needed for it).
        verify(ruleRepository, never()).deleteActiveAndReturn(eq(ruleB.id()), eq(USER_ID));
        verify(firingRepository, times(1)).insert(any(AlertFiring.class));
        verify(emailService, times(1)).enqueue(eq(EventType.DIGEST), eq(USER_ID.value()), any(String.class), any());
    }

    @Test
    void forUser_locksBeforeReadingEndSnapshot() {
        when(getSnapshot.at(USER_ID, RUN_DATE)).thenReturn(Optional.empty());

        evaluateAlerts.forUser(USER_ID, RUN_DATE);

        InOrder order = Mockito.inOrder(lock, getSnapshot);
        order.verify(lock).acquireResourceForUser("alert-eval", USER_ID);
        order.verify(getSnapshot).at(USER_ID, RUN_DATE);
    }

    private static AlertRule upRule(BigDecimal threshold) {
        return new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                Direction.UP,
                new Percentage(threshold),
                WINDOW,
                FIXED_NOW.minusSeconds(3600));
    }

    private static AlertRule downRule(BigDecimal threshold) {
        return new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                Direction.DOWN,
                new Percentage(threshold),
                WINDOW,
                FIXED_NOW.minusSeconds(3600));
    }

    private void stubWindow() {
        when(marketCalendar.mostRecentTradingDayOnOrBefore(RUN_DATE.minusDays(WINDOW.days())))
                .thenReturn(WINDOW_START);
    }

    private void stubUser() {
        User user = Mockito.mock(User.class);
        when(user.email()).thenReturn(USER_EMAIL);
        when(userService.lookup(USER_ID)).thenReturn(user);
    }
}
