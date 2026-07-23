package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListSnapshotsTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final FixedClock CLOCK = new FixedClock(Instant.parse("2026-07-23T12:00:00Z"));
    private static final LocalDate ANCHOR = LocalDate.parse("2026-07-22");
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-22");

    @Mock
    private PortfolioSnapshotRepository repository;

    private ListSnapshots listSnapshots;

    @BeforeEach
    void setUp() {
        listSnapshots = new ListSnapshots(repository, CLOCK);
    }

    @Test
    void list_anchorsAtYesterday_queriesRepositoryWithAnchorMinusRangeToAnchor() {
        when(repository.listByUserAndRange(USER_ID, SnapshotRange.Y1.from(ANCHOR), ANCHOR))
                .thenReturn(List.of());

        listSnapshots.list(USER_ID, SnapshotRange.Y1);

        verify(repository).listByUserAndRange(USER_ID, SnapshotRange.Y1.from(ANCHOR), ANCHOR);
    }

    @Test
    void list_returnsRepositoryResultUnchanged() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(USER_ID, SNAPSHOT_DATE, new Money(new BigDecimal("100.00")));
        when(repository.listByUserAndRange(USER_ID, SnapshotRange.M1.from(ANCHOR), ANCHOR))
                .thenReturn(List.of(snapshot));

        List<PortfolioSnapshot> result = listSnapshots.list(USER_ID, SnapshotRange.M1);

        assertThat(result).containsExactly(snapshot);
    }
}
