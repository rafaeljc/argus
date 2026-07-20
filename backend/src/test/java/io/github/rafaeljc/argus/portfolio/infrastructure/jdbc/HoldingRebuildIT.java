package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class HoldingRebuildIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private HoldingRebuild rebuild;

    @Autowired
    private HoldingRepository repository;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UserId userId;

    @BeforeEach
    void seedForeignKeyDependencies() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        userId = insertUser();
    }

    @Test
    void apply_positiveNetQuantity_upsertsHolding() {
        inLockedTransaction(() -> rebuild.apply(userId, AAPL, new BigDecimal("10")));

        Optional<Holding> holding = repository.find(userId, AAPL);
        assertThat(holding).isPresent();
        assertThat(holding.get().quantity()).isEqualTo(new Quantity(new BigDecimal("10")));
        assertThat(holding.get().updatedAt()).isNotNull();
    }

    @Test
    void apply_positiveNetQuantityAgain_updatesExistingHolding() {
        inLockedTransaction(() -> rebuild.apply(userId, AAPL, new BigDecimal("10")));

        inLockedTransaction(() -> rebuild.apply(userId, AAPL, new BigDecimal("7")));

        Optional<Holding> holding = repository.find(userId, AAPL);
        assertThat(holding).isPresent();
        assertThat(holding.get().quantity()).isEqualTo(new Quantity(new BigDecimal("7")));
    }

    @Test
    void apply_zeroNetQuantityAfterPositive_deletesHolding() {
        inLockedTransaction(() -> rebuild.apply(userId, AAPL, new BigDecimal("10")));
        assertThat(repository.find(userId, AAPL)).isPresent();

        inLockedTransaction(() -> rebuild.apply(userId, AAPL, BigDecimal.ZERO));

        assertThat(repository.find(userId, AAPL)).isEmpty();
    }

    private void inLockedTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> {
            acquireAdvisoryLock(userId);
            work.run();
        });
    }

    private void acquireAdvisoryLock(UserId userId) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended('holding:' || ?, 0))",
                (ResultSetExtractor<Void>) rs -> null,
                userId.value().toString());
    }

    private UserId insertUser() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "holding-rebuild-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
