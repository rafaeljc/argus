package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
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

    @BeforeEach
    void resetCircuitBreakerAndGateway() {
        vendorMarketdataBreaker.reset();
        gateway.reset();
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
