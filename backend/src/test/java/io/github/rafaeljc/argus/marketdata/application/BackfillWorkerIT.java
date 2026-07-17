package io.github.rafaeljc.argus.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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

@Import({PostgresContainer.class, BackfillWorkerIT.TestStubsConfig.class})
@SpringBootTest
class BackfillWorkerIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final LocalDate START = LocalDate.of(2021, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private BackfillWorker worker;

    @Autowired
    private BackfillJobRepository jobs;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private CircuitBreaker vendorMarketdataBreaker;

    @Autowired
    private ProgrammableVendorPriceGateway gateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId userId;

    @BeforeEach
    void resetCircuitBreakerAndGateway() {
        vendorMarketdataBreaker.reset();
        gateway.reset();
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW));
        userId = insertUser();
    }

    @Test
    void processPendingBatch_onePendingJob_upsertsPricesAndCompletesJob() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        gateway.respondWith(AAPL, List.of(price(AAPL, START), price(AAPL, START.plusDays(1))));

        worker.processPendingBatch();

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reloaded.priceCount()).isEqualTo(2);
        assertThat(priceHistoryCount(AAPL)).isEqualTo(2);
    }

    @Test
    void processPendingBatch_multiplePendingJobs_completesAll() {
        gateway.respondWith(AAPL, List.of(price(AAPL, START)));
        gateway.respondWith(MSFT, List.of(price(MSFT, START), price(MSFT, START.plusDays(1))));
        BackfillJob first = jobs.save(pendingJob(AAPL));
        BackfillJob second = jobs.save(pendingJob(MSFT));

        worker.processPendingBatch();

        assertThat(jobs.findById(first.id()).orElseThrow().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(jobs.findById(second.id()).orElseThrow().status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(priceHistoryCount(AAPL)).isEqualTo(1);
        assertThat(priceHistoryCount(MSFT)).isEqualTo(2);
    }

    @Test
    void processPendingBatch_noPendingJobs_isNoOp() {
        worker.processPendingBatch();

        assertThat(gateway.fetchCount()).isZero();
    }

    @Test
    void processPendingBatch_vendorThrows_marksJobFailedWithoutUpsertingPrices() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        gateway.failWith(AAPL, new RuntimeException("vendor 503"));

        worker.processPendingBatch();

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.FAILED);
        assertThat(reloaded.errorMessage()).isEqualTo("vendor 503");
        assertThat(priceHistoryCount(AAPL)).isZero();
    }

    @Test
    void processPendingBatch_breakerForcedOpen_leavesJobPending() {
        gateway.respondWith(AAPL, List.of(price(AAPL, START)));
        vendorMarketdataBreaker.transitionToForcedOpenState();
        BackfillJob saved = jobs.save(pendingJob(AAPL));

        worker.processPendingBatch();

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.PENDING);
        assertThat(gateway.fetchCount()).isZero();
    }

    private int priceHistoryCount(Ticker ticker) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_history WHERE ticker = ?", Integer.class, ticker.value());
        return count == null ? 0 : count;
    }

    private BackfillJob pendingJob(Ticker ticker) {
        return new BackfillJob(
                new JobId(UUID.randomUUID()), ticker, userId, JobStatus.PENDING, START, END,
                null, null, NOW, null, null);
    }

    private PriceHistory price(Ticker ticker, LocalDate tradeDate) {
        return new PriceHistory(ticker, tradeDate, BigDecimal.TEN, false, NOW, NOW);
    }

    private UserId insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "backfill-worker-it-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
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
        private final Map<Ticker, List<PriceHistory>> responses = new HashMap<>();
        private final Map<Ticker, RuntimeException> failures = new HashMap<>();
        private int fetchCount;

        void respondWith(Ticker ticker, List<PriceHistory> prices) {
            responses.put(ticker, prices);
        }

        void failWith(Ticker ticker, RuntimeException exception) {
            failures.put(ticker, exception);
        }

        void reset() {
            responses.clear();
            failures.clear();
            fetchCount = 0;
        }

        int fetchCount() {
            return fetchCount;
        }

        @Override
        public Set<Symbol> fetchSymbolUniverse() {
            return Set.of();
        }

        @Override
        public List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end) {
            fetchCount++;
            RuntimeException failure = failures.get(ticker);
            if (failure != null) {
                throw failure;
            }
            List<PriceHistory> prices = responses.get(ticker);
            if (prices == null) {
                throw new IllegalStateException("no stub registered for " + ticker);
            }
            return prices;
        }
    }
}
