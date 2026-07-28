package io.github.rafaeljc.argus.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
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
class ListFiringsTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private AlertFiringRepository repository;

    private ListFirings listFirings;

    @BeforeEach
    void setUp() {
        listFirings = new ListFirings(repository);
    }

    @Test
    void list_delegatesPageAndPerPageToRepositoryAndReturnsItemsWithTotal() {
        AlertFiring firing = new AlertFiring(
                new FiringId(UuidCreator.getTimeOrderedEpoch()), USER_ID,
                new RuleId(UuidCreator.getTimeOrderedEpoch()), Direction.UP,
                new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30),
                Instant.parse("2026-07-01T00:00:00Z"), new Money(new BigDecimal("1000.00")),
                new Money(new BigDecimal("1050.00")), new BigDecimal("5.00"),
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01"));
        when(repository.listByUserOrderedByFiredAtDesc(USER_ID, 2, 25)).thenReturn(List.of(firing));
        when(repository.countByUser(USER_ID)).thenReturn(30);

        PageResult<AlertFiring> result = listFirings.list(USER_ID, 2, 25);

        assertThat(result.items()).containsExactly(firing);
        assertThat(result.total()).isEqualTo(30);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.perPage()).isEqualTo(25);
    }

    @Test
    void list_noFirings_returnsEmptyPageWithZeroTotal() {
        when(repository.listByUserOrderedByFiredAtDesc(USER_ID, 1, 50)).thenReturn(List.of());
        when(repository.countByUser(USER_ID)).thenReturn(0);

        PageResult<AlertFiring> result = listFirings.list(USER_ID, 1, 50);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
