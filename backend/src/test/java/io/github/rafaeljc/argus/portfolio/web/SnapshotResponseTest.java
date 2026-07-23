package io.github.rafaeljc.argus.portfolio.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SnapshotResponseTest {

    @Test
    void from_projectsContractFields() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                new UserId(UuidCreator.getTimeOrderedEpoch()),
                LocalDate.parse("2026-06-22"),
                new Money(new BigDecimal("1234.50")));

        SnapshotResponse response = SnapshotResponse.from(snapshot);

        assertThat(response.snapshotDate()).isEqualTo(LocalDate.parse("2026-06-22"));
        assertThat(response.totalValue()).isEqualTo("1234.50");
    }
}
