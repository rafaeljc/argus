package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetActiveHoldingsTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private HoldingRepository repository;

    private GetActiveHoldings getActiveHoldings;

    @BeforeEach
    void setUp() {
        getActiveHoldings = new GetActiveHoldings(repository);
    }

    @Test
    void forUser_delegatesToRepositoryFindByUser() {
        Holding holding = new Holding(USER_ID, AAPL, new Quantity(new BigDecimal("10")), UPDATED_AT);
        when(repository.findByUser(USER_ID)).thenReturn(List.of(holding));

        List<Holding> holdings = getActiveHoldings.forUser(USER_ID);

        assertThat(holdings).containsExactly(holding);
    }

    @Test
    void forUser_noHoldings_returnsEmptyList() {
        when(repository.findByUser(USER_ID)).thenReturn(List.of());

        assertThat(getActiveHoldings.forUser(USER_ID)).isEmpty();
    }
}
