package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcPortfolioSnapshotRepositoryIT {

    private static final LocalDate D1 = LocalDate.parse("2026-06-01");
    private static final LocalDate D2 = LocalDate.parse("2026-06-02");
    private static final LocalDate D3 = LocalDate.parse("2026-06-03");

    @Autowired
    private PortfolioSnapshotRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void insertIfAbsent_thenFindByUserAndDate_returnsPersistedSnapshot() {
        UserId userId = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("1000.00"))));

        Optional<PortfolioSnapshot> found = repository.findByUserAndDate(userId, D1);

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().snapshotDate()).isEqualTo(D1);
        assertThat(found.get().totalValue()).isEqualTo(new Money(new BigDecimal("1000.00")));
    }

    @Test
    void findByUserAndDate_noRow_returnsEmpty() {
        UserId userId = newUser();

        assertThat(repository.findByUserAndDate(userId, D1)).isEmpty();
    }

    @Test
    void insertIfAbsent_secondCallSameUserAndDate_leavesFirstValueUnchanged() {
        UserId userId = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("1000.00"))));

        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("9999.00"))));

        Optional<PortfolioSnapshot> found = repository.findByUserAndDate(userId, D1);
        assertThat(found).isPresent();
        assertThat(found.get().totalValue()).isEqualTo(new Money(new BigDecimal("1000.00")));
    }

    @Test
    void listByUserAndRange_ordersByDateDescending() {
        UserId userId = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("100.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D3, new Money(new BigDecimal("300.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D2, new Money(new BigDecimal("200.00"))));

        List<PortfolioSnapshot> page = repository.listByUserAndRange(userId, null, null, 1, 50);

        assertThat(page).extracting(PortfolioSnapshot::snapshotDate).containsExactly(D3, D2, D1);
    }

    @Test
    void listByUserAndRange_boundedRange_excludesOutsideDates() {
        UserId userId = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("100.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D2, new Money(new BigDecimal("200.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D3, new Money(new BigDecimal("300.00"))));

        List<PortfolioSnapshot> ranged = repository.listByUserAndRange(userId, D2, D2, 1, 50);

        assertThat(ranged).extracting(PortfolioSnapshot::snapshotDate).containsExactly(D2);
    }

    @Test
    void listByUserAndRange_pagination_slicesCorrectly() {
        UserId userId = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D1, new Money(new BigDecimal("100.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D2, new Money(new BigDecimal("200.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(userId, D3, new Money(new BigDecimal("300.00"))));

        List<PortfolioSnapshot> pageOne = repository.listByUserAndRange(userId, null, null, 1, 2);
        List<PortfolioSnapshot> pageTwo = repository.listByUserAndRange(userId, null, null, 2, 2);

        assertThat(pageOne).extracting(PortfolioSnapshot::snapshotDate).containsExactly(D3, D2);
        assertThat(pageTwo).extracting(PortfolioSnapshot::snapshotDate).containsExactly(D1);
    }

    @Test
    void listByUserAndRange_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.insertIfAbsent(new PortfolioSnapshot(owner, D1, new Money(new BigDecimal("100.00"))));
        repository.insertIfAbsent(new PortfolioSnapshot(otherUser, D1, new Money(new BigDecimal("500.00"))));

        List<PortfolioSnapshot> page = repository.listByUserAndRange(owner, null, null, 1, 50);

        assertThat(page).extracting(PortfolioSnapshot::userId).containsExactly(owner);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
