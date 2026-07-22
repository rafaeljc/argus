package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcHoldingRepositoryIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private HoldingRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private SymbolRepository symbols;

    @BeforeEach
    void seedSymbols() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW));
    }

    @Test
    void findByUser_multipleHoldings_returnsAllForThatUser() {
        UserId userId = newUser();
        repository.upsert(userId, AAPL, new Quantity(new BigDecimal("10")), NOW);
        repository.upsert(userId, MSFT, new Quantity(new BigDecimal("5")), NOW);

        List<Holding> holdings = repository.findByUser(userId);

        assertThat(holdings).extracting(Holding::ticker).containsExactlyInAnyOrder(AAPL, MSFT);
    }

    @Test
    void findByUser_noHoldings_returnsEmptyList() {
        UserId userId = newUser();

        assertThat(repository.findByUser(userId)).isEmpty();
    }

    @Test
    void findByUser_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.upsert(owner, AAPL, new Quantity(new BigDecimal("10")), NOW);
        repository.upsert(otherUser, MSFT, new Quantity(new BigDecimal("5")), NOW);

        assertThat(repository.findByUser(owner)).extracting(Holding::ticker).containsExactly(AAPL);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
