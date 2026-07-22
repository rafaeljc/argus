package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSnapshotTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-22");

    @Mock
    private PortfolioSnapshotRepository repository;

    private GetSnapshot getSnapshot;

    @BeforeEach
    void setUp() {
        getSnapshot = new GetSnapshot(repository);
    }

    @Test
    void at_delegatesToRepositoryFindByUserAndDate() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(USER_ID, SNAPSHOT_DATE, new Money(new BigDecimal("100.00")));
        when(repository.findByUserAndDate(USER_ID, SNAPSHOT_DATE)).thenReturn(Optional.of(snapshot));

        Optional<PortfolioSnapshot> found = getSnapshot.at(USER_ID, SNAPSHOT_DATE);

        assertThat(found).contains(snapshot);
    }

    @Test
    void at_noSnapshot_returnsEmpty() {
        when(repository.findByUserAndDate(USER_ID, SNAPSHOT_DATE)).thenReturn(Optional.empty());

        assertThat(getSnapshot.at(USER_ID, SNAPSHOT_DATE)).isEmpty();
    }
}
