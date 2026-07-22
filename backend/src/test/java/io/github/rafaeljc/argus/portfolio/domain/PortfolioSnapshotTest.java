package io.github.rafaeljc.argus.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioSnapshotTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-22");
    private static final Money TOTAL_VALUE = new Money(new BigDecimal("1000.00"));

    private static PortfolioSnapshot newSnapshot() {
        return new PortfolioSnapshot(USER_ID, SNAPSHOT_DATE, TOTAL_VALUE);
    }

    @Test
    void constructor_validInput_setsAllFields() {
        PortfolioSnapshot snapshot = newSnapshot();

        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.snapshotDate()).isEqualTo(SNAPSHOT_DATE);
        assertThat(snapshot.totalValue()).isEqualTo(TOTAL_VALUE);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioSnapshot(null, SNAPSHOT_DATE, TOTAL_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void constructor_nullSnapshotDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioSnapshot(USER_ID, null, TOTAL_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshotDate");
    }

    @Test
    void constructor_nullTotalValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioSnapshot(USER_ID, SNAPSHOT_DATE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalValue");
    }
}
