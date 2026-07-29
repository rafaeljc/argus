package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class EvaluateAlertsIT {

    private static final LocalDate RUN_DATE = LocalDate.parse("2026-07-01");
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRuleRepository ruleRepository;

    @Autowired
    private AlertFiringRepository firingRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private MarketCalendar marketCalendar;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void evaluateForUser_ruleFires_deletesRuleInsertsFiringAndEnqueuesOutbox() {
        UserId userId = newUser();
        seedSnapshots(userId, "10000.00", "10600.00");
        AlertRule rule = ruleRepository.insert(upRule(userId, "5.0"));

        alertService.evaluateForUser(userId, RUN_DATE);

        assertThat(ruleRepository.findActiveByIdAndUser(rule.id(), userId)).isEmpty();

        List<AlertFiring> firings = firingRepository.listByUserOrderedByFiredAtDesc(userId, 1, 50);
        assertThat(firings).hasSize(1);
        AlertFiring firing = firings.get(0);
        assertThat(firing.ruleId()).isEqualTo(rule.id());
        assertThat(firing.percentChange()).isEqualByComparingTo(new BigDecimal("6.00"));

        String idempotenceKey = "digest:" + userId.value() + ":" + RUN_DATE;
        assertThat(countOutboxRows(idempotenceKey)).isEqualTo(1);
    }

    @Test
    void evaluateForUser_calledTwiceSameRunDate_secondCallAddsNoRows() {
        UserId userId = newUser();
        seedSnapshots(userId, "10000.00", "10600.00");
        ruleRepository.insert(upRule(userId, "5.0"));

        alertService.evaluateForUser(userId, RUN_DATE);
        alertService.evaluateForUser(userId, RUN_DATE);

        assertThat(firingRepository.listByUserOrderedByFiredAtDesc(userId, 1, 50)).hasSize(1);
        String idempotenceKey = "digest:" + userId.value() + ":" + RUN_DATE;
        assertThat(countOutboxRows(idempotenceKey)).isEqualTo(1);
    }

    @Test
    void evaluateForUser_concurrentCallsSameUserAndRunDate_serializeToAtMostOneFiring() throws Exception {
        UserId userId = newUser();
        seedSnapshots(userId, "10000.00", "10600.00");
        ruleRepository.insert(upRule(userId, "5.0"));
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(
                    () -> alertService.evaluateForUser(userId, RUN_DATE), pool);
            CompletableFuture<Void> second = CompletableFuture.runAsync(
                    () -> alertService.evaluateForUser(userId, RUN_DATE), pool);
            CompletableFuture.allOf(first, second).get();
        } finally {
            pool.shutdown();
        }

        assertThat(firingRepository.listByUserOrderedByFiredAtDesc(userId, 1, 50)).hasSize(1);
        String idempotenceKey = "digest:" + userId.value() + ":" + RUN_DATE;
        assertThat(countOutboxRows(idempotenceKey)).isEqualTo(1);
    }

    @Test
    void evaluateForUser_missingStartSnapshot_skipsRuleWithoutException() {
        UserId userId = newUser();
        Money end = new Money(new BigDecimal("10600.00"));
        snapshotRepository.insertIfAbsent(new PortfolioSnapshot(userId, RUN_DATE, end));
        AlertRule rule = ruleRepository.insert(upRule(userId, "5.0"));

        assertThatNoException().isThrownBy(() -> alertService.evaluateForUser(userId, RUN_DATE));

        assertThat(ruleRepository.findActiveByIdAndUser(rule.id(), userId)).isPresent();
        assertThat(firingRepository.listByUserOrderedByFiredAtDesc(userId, 1, 50)).isEmpty();
    }

    private void seedSnapshots(UserId userId, String startValue, String endValue) {
        LocalDate windowStart = marketCalendar.mostRecentTradingDayOnOrBefore(RUN_DATE.minusDays(WINDOW.days()));
        Money start = new Money(new BigDecimal(startValue));
        Money end = new Money(new BigDecimal(endValue));
        snapshotRepository.insertIfAbsent(new PortfolioSnapshot(userId, windowStart, start));
        snapshotRepository.insertIfAbsent(new PortfolioSnapshot(userId, RUN_DATE, end));
    }

    private int countOutboxRows(String idempotenceKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE idempotence_key = ?", Integer.class, idempotenceKey);
        return count == null ? 0 : count;
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static AlertRule upRule(UserId userId, String threshold) {
        return new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                Direction.UP,
                new Percentage(new BigDecimal(threshold)),
                WINDOW,
                Instant.parse("2026-06-01T00:00:00Z"));
    }
}
