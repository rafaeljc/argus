package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
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

@Import({PostgresContainer.class, EodPipelineServiceIT.TestStubsConfig.class})
@SpringBootTest
class EodPipelineServiceIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker MISSING = new Ticker("MISSING");
    private static final Instant PAST = Instant.parse("2026-06-01T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

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

        void respondWith(Set<Symbol> universe) {
            this.universe = new HashSet<>(universe);
        }

        void reset() {
            universe = Set.of();
        }

        @Override
        public Set<Symbol> fetchSymbolUniverse() {
            return universe;
        }

        @Override
        public List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end) {
            throw new UnsupportedOperationException("not used by EodPipelineServiceIT");
        }
    }
}
