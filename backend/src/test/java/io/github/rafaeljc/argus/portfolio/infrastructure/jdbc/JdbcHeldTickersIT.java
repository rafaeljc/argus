package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcHeldTickersIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GOOG = new Ticker("GOOG");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private HeldTickers heldTickers;

    @Autowired
    private HoldingRepository holdings;

    @Autowired
    private UserService userService;

    @Autowired
    private SymbolRepository symbols;

    @BeforeEach
    void seedSymbols() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW));
        symbols.save(new Symbol(GOOG, Exchange.NASDAQ, "Alphabet Inc.", false, NOW, NOW, NOW));
    }

    @Test
    void findForUserIds_emptyUserIds_returnsEmptySetWithoutQuerying() {
        assertThat(heldTickers.findForUserIds(List.of())).isEmpty();
    }

    @Test
    void findForUserIds_userWithNoHoldings_returnsEmptySet() {
        UserId user = newUser();

        assertThat(heldTickers.findForUserIds(List.of(user))).isEmpty();
    }

    @Test
    void findForUserIds_singleUser_returnsHeldTickers() {
        UserId user = newUser();
        holdings.upsert(user, AAPL, new Quantity(new BigDecimal("10")), NOW);
        holdings.upsert(user, MSFT, new Quantity(new BigDecimal("5")), NOW);

        assertThat(heldTickers.findForUserIds(List.of(user))).containsExactlyInAnyOrder(AAPL, MSFT);
    }

    @Test
    void findForUserIds_multipleUsers_dedupesOverlappingTickers() {
        UserId first = newUser();
        UserId second = newUser();
        holdings.upsert(first, AAPL, new Quantity(new BigDecimal("10")), NOW);
        holdings.upsert(second, AAPL, new Quantity(new BigDecimal("3")), NOW);
        holdings.upsert(second, MSFT, new Quantity(new BigDecimal("5")), NOW);

        Set<Ticker> result = heldTickers.findForUserIds(List.of(first, second));

        assertThat(result).containsExactlyInAnyOrder(AAPL, MSFT);
    }

    @Test
    void findForUserIds_scopedToGivenUsers_excludesOthers() {
        UserId included = newUser();
        UserId excluded = newUser();
        holdings.upsert(included, AAPL, new Quantity(new BigDecimal("10")), NOW);
        holdings.upsert(excluded, GOOG, new Quantity(new BigDecimal("2")), NOW);

        assertThat(heldTickers.findForUserIds(List.of(included))).containsExactly(AAPL);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
