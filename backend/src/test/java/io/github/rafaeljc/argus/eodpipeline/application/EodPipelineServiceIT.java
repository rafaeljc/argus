package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.GetActiveHoldings;
import io.github.rafaeljc.argus.portfolio.application.GetPortfolio;
import io.github.rafaeljc.argus.portfolio.application.GetSnapshot;
import io.github.rafaeljc.argus.portfolio.application.ListSnapshots;
import io.github.rafaeljc.argus.portfolio.application.PortfolioService;
import io.github.rafaeljc.argus.portfolio.application.SnapshotWriter;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import({PostgresContainer.class, EodPipelineServiceIT.TestStubsConfig.class})
@SpringBootTest
class EodPipelineServiceIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GOOG = new Ticker("GOOG");
    private static final Ticker MISSING = new Ticker("MISSING");
    private static final Instant PAST = Instant.parse("2026-06-01T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final LocalDate EVAL_RUN_DATE = LocalDate.of(2026, 7, 1);
    private static final AlertLookbackWindow WINDOW = new AlertLookbackWindow(30);

    @Autowired
    private EodPipelineService service;

    @Autowired
    private EodPipelineRunRepository runs;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private CircuitBreaker vendorMarketdataBreaker;

    @Autowired
    private ProgrammableVendorPriceGateway gateway;

    @Autowired
    private HoldingRepository holdings;

    @Autowired
    private UserService userService;

    @Autowired
    private PriceLookup priceLookup;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailureInjectingPortfolioService portfolioService;

    @Autowired
    private PortfolioSnapshotRepository snapshots;

    @Autowired
    private AlertRuleRepository alertRules;

    @Autowired
    private AlertFiringRepository alertFirings;

    @Autowired
    private MarketCalendar marketCalendar;

    @Autowired
    private PriceHistoryRepository priceHistory;

    @BeforeEach
    void resetCircuitBreakerAndGateway() {
        vendorMarketdataBreaker.reset();
        gateway.reset();
        portfolioService.reset();
    }

    @Test
    void runSymbols_vendorSucceeds_reconcilesUniverseAndMarksStepSucceeded() {
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, PAST, PAST, PAST));
        symbols.save(new Symbol(MISSING, Exchange.NASDAQ, "Missing Corp.", false, PAST, PAST, PAST));
        gateway.respondWith(Set.of(
                new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, PAST, PAST, PAST),
                new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, PAST, PAST, PAST)));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 22)));

        EodPipelineRun result = service.runSymbols(run.id());

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.errorMessage()).isNull();
        Symbol newTicker = symbols.findByTicker(AAPL).orElseThrow();
        assertThat(newTicker.isDelisted()).isFalse();
        Symbol survivingTicker = symbols.findByTicker(MSFT).orElseThrow();
        assertThat(survivingTicker.isDelisted()).isFalse();
        assertThat(survivingTicker.lastVendorCheck()).isAfter(PAST);
        Symbol delistedTicker = symbols.findByTicker(MISSING).orElseThrow();
        assertThat(delistedTicker.isDelisted()).isTrue();
        EodPipelineRun updated = runs.findById(run.id()).orElseThrow();
        assertThat(updated).isEqualTo(result);
    }

    @Test
    void runSymbols_breakerForcedOpen_marksStepAndRunFailedAndLeavesSymbolsUntouched() {
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, PAST, PAST, PAST));
        gateway.respondWith(Set.of(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, PAST, PAST, PAST)));
        vendorMarketdataBreaker.transitionToForcedOpenState();
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 23)));

        EodPipelineRun result = service.runSymbols(run.id());

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.errorMessage()).isNotBlank();
        EodPipelineRun updated = runs.findById(run.id()).orElseThrow();
        assertThat(updated).isEqualTo(result);
        Symbol untouched = symbols.findByTicker(MSFT).orElseThrow();
        assertThat(untouched.isDelisted()).isFalse();
        assertThat(untouched.lastVendorCheck()).isEqualTo(PAST);
    }

    @Test
    void runPrices_noHeldTickers_succeedsWithoutCallingVendor() {
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 22)));

        EodPipelineRun result = service.runPrices(run.id());

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.errorMessage()).isNull();
        assertThat(gateway.lastRequestedTickers()).isEmpty();
    }

    @Test
    void runPrices_heldTickersAcrossActiveUsers_upsertsClosesAndMarksStepSucceeded() {
        seedSymbol(AAPL);
        seedSymbol(MSFT);
        UserId first = newActiveUser();
        UserId second = newActiveUser();
        holdings.upsert(first, AAPL, new Quantity(new BigDecimal("10")), PAST);
        holdings.upsert(second, MSFT, new Quantity(new BigDecimal("5")), PAST);
        LocalDate runDate = LocalDate.of(2026, 6, 22);
        gateway.respondWithCloses(Map.of(AAPL, new BigDecimal("150.25"), MSFT, new BigDecimal("310.10")));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), runDate));

        EodPipelineRun result = service.runPrices(run.id());

        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.errorMessage()).isNull();
        assertThat(gateway.lastRequestedTickers()).containsExactlyInAnyOrder(AAPL, MSFT);
        assertThat(priceLookup.closeOn(AAPL, runDate))
                .hasValueSatisfying(close -> assertThat(close).isEqualByComparingTo("150.25"));
        assertThat(priceLookup.closeOn(MSFT, runDate))
                .hasValueSatisfying(close -> assertThat(close).isEqualByComparingTo("310.10"));
        EodPipelineRun updated = runs.findById(run.id()).orElseThrow();
        assertThat(updated).isEqualTo(result);
    }

    @Test
    void runPrices_suspendedAndDeletedUsersExcludedFromHeldTickers() {
        seedSymbol(AAPL);
        seedSymbol(MSFT);
        seedSymbol(GOOG);
        UserId active = newActiveUser();
        UserId suspended = newActiveUser();
        UserId deleted = newActiveUser();
        holdings.upsert(active, AAPL, new Quantity(new BigDecimal("1")), PAST);
        holdings.upsert(suspended, MSFT, new Quantity(new BigDecimal("1")), PAST);
        holdings.upsert(deleted, GOOG, new Quantity(new BigDecimal("1")), PAST);
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", suspended.value());
        userService.softDelete(deleted, RAW_PASSWORD);
        gateway.respondWithCloses(Map.of(
                AAPL, new BigDecimal("150.25"), MSFT, new BigDecimal("310.10"), GOOG, new BigDecimal("2800.00")));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 22)));

        service.runPrices(run.id());

        assertThat(gateway.lastRequestedTickers()).containsExactly(AAPL);
    }

    @Test
    void runPrices_rerunSameRunDate_isIdempotentAndOverwritesClose() {
        seedSymbol(AAPL);
        UserId user = newActiveUser();
        holdings.upsert(user, AAPL, new Quantity(new BigDecimal("1")), PAST);
        LocalDate runDate = LocalDate.of(2026, 6, 22);
        gateway.respondWithCloses(Map.of(AAPL, new BigDecimal("150.25")));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), runDate));
        service.runPrices(run.id());

        gateway.respondWithCloses(Map.of(AAPL, new BigDecimal("151.50")));
        EodPipelineRun result = service.runPrices(run.id());

        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(priceLookup.closeOn(AAPL, runDate))
                .hasValueSatisfying(close -> assertThat(close).isEqualByComparingTo("151.50"));
        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM price_history WHERE ticker = ? AND trade_date = ?",
                Integer.class, AAPL.value(), runDate);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void runPrices_breakerForcedOpen_marksStepAndRunFailedAndLeavesPricesUntouched() {
        seedSymbol(AAPL);
        UserId user = newActiveUser();
        holdings.upsert(user, AAPL, new Quantity(new BigDecimal("1")), PAST);
        LocalDate runDate = LocalDate.of(2026, 6, 22);
        gateway.respondWithCloses(Map.of(AAPL, new BigDecimal("150.25")));
        vendorMarketdataBreaker.transitionToForcedOpenState();
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), runDate));

        EodPipelineRun result = service.runPrices(run.id());

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.errorMessage()).isNotBlank();
        EodPipelineRun updated = runs.findById(run.id()).orElseThrow();
        assertThat(updated).isEqualTo(result);
        assertThat(priceLookup.closeOn(AAPL, runDate)).isEmpty();
    }

    @Test
    void runEvaluate_activeUserWithFiringRule_writesSnapshotFiresRuleAndEnqueuesDigest() {
        seedSymbol(AAPL);
        UserId userId = newActiveUser();
        LocalDate windowStart = marketCalendar.mostRecentTradingDayOnOrBefore(EVAL_RUN_DATE.minusDays(WINDOW.days()));
        snapshots.insertIfAbsent(new PortfolioSnapshot(userId, windowStart, new Money(new BigDecimal("10000.00"))));
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("100")), PAST);
        priceHistory.upsertBatch(
                List.of(new PriceHistory(AAPL, EVAL_RUN_DATE, new BigDecimal("106.00"), true, PAST, PAST)));
        AlertRule rule = alertRules.insert(upRule(userId, "5.0"));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), EVAL_RUN_DATE));

        EodPipelineRun result = service.runEvaluate(run.id());

        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.errorMessage()).isNull();
        assertThat(snapshots.findByUserAndDate(userId, EVAL_RUN_DATE))
                .hasValueSatisfying(s -> assertThat(s.totalValue().value()).isEqualByComparingTo("10600.00"));
        assertThat(alertRules.findActiveByIdAndUser(rule.id(), userId)).isEmpty();
        assertThat(alertFirings.listByUserOrderedByFiredAtDesc(userId, 1, 50)).hasSize(1);
        assertThat(countOutboxRows("digest:" + userId.value() + ":" + EVAL_RUN_DATE)).isEqualTo(1);
        EodPipelineRun updated = runs.findById(run.id()).orElseThrow();
        assertThat(updated).isEqualTo(result);
    }

    @Test
    void runEvaluate_noActiveUsers_marksStepSucceededWithoutWritingAnySnapshot() {
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), EVAL_RUN_DATE));

        EodPipelineRun result = service.runEvaluate(run.id());

        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void runEvaluate_oneUserFailsAnotherSucceeds_marksStepFailedButKeepsSucceedingUsersCommittedWork() {
        UserId failingUser = newActiveUser();
        final UserId succeedingUser = newActiveUser();
        portfolioService.failFor(failingUser);
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), EVAL_RUN_DATE));

        EodPipelineRun result = service.runEvaluate(run.id());

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("evaluate failed for 1 of 2 users");
        assertThat(snapshots.findByUserAndDate(failingUser, EVAL_RUN_DATE)).isEmpty();
        assertThat(snapshots.findByUserAndDate(succeedingUser, EVAL_RUN_DATE))
                .hasValueSatisfying(s -> assertThat(s.totalValue().value()).isEqualByComparingTo("0.00"));
    }

    @Test
    void runEvaluate_rerunSameRunDate_isIdempotentForSnapshotAndDigest() {
        seedSymbol(AAPL);
        UserId userId = newActiveUser();
        LocalDate windowStart = marketCalendar.mostRecentTradingDayOnOrBefore(EVAL_RUN_DATE.minusDays(WINDOW.days()));
        snapshots.insertIfAbsent(new PortfolioSnapshot(userId, windowStart, new Money(new BigDecimal("10000.00"))));
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("100")), PAST);
        priceHistory.upsertBatch(
                List.of(new PriceHistory(AAPL, EVAL_RUN_DATE, new BigDecimal("106.00"), true, PAST, PAST)));
        alertRules.insert(upRule(userId, "5.0"));
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), EVAL_RUN_DATE));

        service.runEvaluate(run.id());
        EodPipelineRun result = service.runEvaluate(run.id());

        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(alertFirings.listByUserOrderedByFiredAtDesc(userId, 1, 50)).hasSize(1);
        assertThat(countOutboxRows("digest:" + userId.value() + ":" + EVAL_RUN_DATE)).isEqualTo(1);
    }

    @Test
    void runEvaluate_suspendedAndDeletedUsersExcluded_noSnapshotWritten() {
        final UserId active = newActiveUser();
        UserId suspended = newActiveUser();
        UserId deleted = newActiveUser();
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", suspended.value());
        userService.softDelete(deleted, RAW_PASSWORD);
        EodPipelineRun run = runs.insert(pendingRun(newRunId(), EVAL_RUN_DATE));

        service.runEvaluate(run.id());

        assertThat(snapshots.findByUserAndDate(active, EVAL_RUN_DATE)).isPresent();
        assertThat(snapshots.findByUserAndDate(suspended, EVAL_RUN_DATE)).isEmpty();
        assertThat(snapshots.findByUserAndDate(deleted, EVAL_RUN_DATE)).isEmpty();
    }

    private int countOutboxRows(String idempotenceKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE idempotence_key = ?", Integer.class, idempotenceKey);
        return count == null ? 0 : count;
    }

    private static AlertRule upRule(UserId userId, String threshold) {
        return new AlertRule(
                new RuleId(UUID.randomUUID()), userId, Direction.UP, new Percentage(new BigDecimal(threshold)),
                WINDOW, PAST);
    }

    private void seedSymbol(Ticker ticker) {
        symbols.save(new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, PAST, PAST, PAST));
    }

    private UserId newActiveUser() {
        return userService.createUnverified(
                "user-" + UUID.randomUUID() + "@example.com", RAW_PASSWORD).id();
    }

    private static EodPipelineRun pendingRun(RunId id, LocalDate runDate) {
        return new EodPipelineRun(
                id, runDate, Trigger.CRON, RunStatus.PENDING, PAST, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    private static RunId newRunId() {
        return new RunId(UUID.randomUUID());
    }

    @TestConfiguration
    static class TestStubsConfig {
        @Bean
        @Primary
        ProgrammableVendorPriceGateway programmableVendorPriceGateway() {
            return new ProgrammableVendorPriceGateway();
        }

        @Bean
        @Primary
        FailureInjectingPortfolioService failureInjectingPortfolioService(
                SnapshotWriter snapshotWriter,
                GetSnapshot getSnapshot,
                GetActiveHoldings getActiveHoldings,
                GetPortfolio getPortfolio,
                ListSnapshots listSnapshots) {
            return new FailureInjectingPortfolioService(
                    snapshotWriter, getSnapshot, getActiveHoldings, getPortfolio, listSnapshots);
        }
    }

    // Lets one test simulate a mid-pipeline failure for a single user without breaking the
    // REQUIRES_NEW transactional semantics that the rest of WriteSnapshotAndEvaluateAlerts relies
    // on: the failing call throws before any write happens, so it never opens a savepoint of its
    // own, and other users' already-committed work is provably untouched by it.
    static class FailureInjectingPortfolioService extends PortfolioService {
        private final AtomicReference<UserId> failingUser = new AtomicReference<>();

        FailureInjectingPortfolioService(
                SnapshotWriter snapshotWriter,
                GetSnapshot getSnapshot,
                GetActiveHoldings getActiveHoldings,
                GetPortfolio getPortfolio,
                ListSnapshots listSnapshots) {
            super(snapshotWriter, getSnapshot, getActiveHoldings, getPortfolio, listSnapshots);
        }

        void failFor(UserId userId) {
            failingUser.set(userId);
        }

        void reset() {
            failingUser.set(null);
        }

        @Override
        public void writeSnapshot(UserId userId, LocalDate snapshotDate) {
            if (userId.equals(failingUser.get())) {
                throw new RuntimeException("simulated snapshot failure");
            }
            super.writeSnapshot(userId, snapshotDate);
        }
    }

    static final class ProgrammableVendorPriceGateway implements VendorPriceGateway {
        private Set<Symbol> universe = Set.of();
        private Map<Ticker, BigDecimal> closes = Map.of();
        private Set<Ticker> lastRequestedTickers = Set.of();

        void respondWith(Set<Symbol> universe) {
            this.universe = new HashSet<>(universe);
        }

        void respondWithCloses(Map<Ticker, BigDecimal> closes) {
            this.closes = new HashMap<>(closes);
        }

        Set<Ticker> lastRequestedTickers() {
            return lastRequestedTickers;
        }

        void reset() {
            universe = Set.of();
            closes = Map.of();
            lastRequestedTickers = Set.of();
        }

        @Override
        public Set<Symbol> fetchSymbolUniverse() {
            return universe;
        }

        @Override
        public List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end) {
            throw new UnsupportedOperationException("not used by EodPipelineServiceIT");
        }

        @Override
        public List<PriceHistory> fetchClosesOn(Set<Ticker> tickers, LocalDate tradeDate) {
            lastRequestedTickers = new HashSet<>(tickers);
            Instant now = Instant.now();
            return tickers.stream()
                    .filter(closes::containsKey)
                    .map(ticker -> new PriceHistory(ticker, tradeDate, closes.get(ticker), true, now, now))
                    .toList();
        }
    }
}
