package io.github.rafaeljc.argus.alerts.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcAlertFiringRepositoryIT {

    @Autowired
    private AlertFiringRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void insert_thenFindByIdAndUser_returnsPersistedFiring() {
        UserId userId = newUser();
        AlertFiring saved = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-07-01T00:00:00Z"),
                "1000.00", "1100.00", "10.00",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01")));

        Optional<AlertFiring> loaded = repository.findByIdAndUser(saved.id(), userId);

        assertThat(loaded).isPresent();
        AlertFiring firing = loaded.get();
        assertThat(firing.id()).isEqualTo(saved.id());
        assertThat(firing.userId()).isEqualTo(userId);
        assertThat(firing.ruleId()).isEqualTo(saved.ruleId());
        assertThat(firing.direction()).isEqualTo(Direction.UP);
        assertThat(firing.threshold()).isEqualTo(new Percentage(new BigDecimal("5.0")));
        assertThat(firing.window()).isEqualTo(new AlertLookbackWindow(30));
        assertThat(firing.firedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(firing.portfolioValueStart()).isEqualTo(new Money(new BigDecimal("1000.00")));
        assertThat(firing.portfolioValueEnd()).isEqualTo(new Money(new BigDecimal("1100.00")));
        assertThat(firing.percentChange()).isEqualByComparingTo("10.00");
        assertThat(firing.windowStartDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(firing.windowEndDate()).isEqualTo(LocalDate.parse("2026-07-01"));
    }

    @Test
    void findByIdAndUser_unknownId_returnsEmpty() {
        UserId userId = newUser();
        FiringId unknown = new FiringId(UuidCreator.getTimeOrderedEpoch());

        assertThat(repository.findByIdAndUser(unknown, userId)).isEmpty();
    }

    @Test
    void findByIdAndUser_differentOwner_returnsEmpty() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        AlertFiring saved = repository.insert(newFiring(
                owner, Direction.UP, "5.0", 30, Instant.parse("2026-07-01T00:00:00Z"),
                "1000.00", "1100.00", "10.00",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01")));

        assertThat(repository.findByIdAndUser(saved.id(), otherUser)).isEmpty();
    }

    @Test
    void listByUserOrderedByFiredAtDesc_ordersNewestFirst() {
        UserId userId = newUser();
        AlertFiring oldest = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-01-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2025-12-01"), LocalDate.parse("2026-01-01")));
        AlertFiring newest = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-03-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01")));
        AlertFiring middle = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-02-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01")));

        List<AlertFiring> page = repository.listByUserOrderedByFiredAtDesc(userId, 1, 50);

        assertThat(page).extracting(AlertFiring::id).containsExactly(newest.id(), middle.id(), oldest.id());
    }

    @Test
    void listByUserOrderedByFiredAtDesc_pagination_slicesCorrectly() {
        UserId userId = newUser();
        AlertFiring first = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-03-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01")));
        AlertFiring second = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-02-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01")));
        AlertFiring third = repository.insert(newFiring(
                userId, Direction.UP, "5.0", 30, Instant.parse("2026-01-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2025-12-01"), LocalDate.parse("2026-01-01")));

        List<AlertFiring> pageOne = repository.listByUserOrderedByFiredAtDesc(userId, 1, 2);
        List<AlertFiring> pageTwo = repository.listByUserOrderedByFiredAtDesc(userId, 2, 2);

        assertThat(pageOne).extracting(AlertFiring::id).containsExactly(first.id(), second.id());
        assertThat(pageTwo).extracting(AlertFiring::id).containsExactly(third.id());
    }

    @Test
    void listByUserOrderedByFiredAtDesc_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.insert(newFiring(
                owner, Direction.UP, "5.0", 30, Instant.parse("2026-01-01T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2025-12-01"), LocalDate.parse("2026-01-01")));
        repository.insert(newFiring(
                otherUser, Direction.UP, "5.0", 30, Instant.parse("2026-01-02T00:00:00Z"),
                "1000.00", "1050.00", "5.00",
                LocalDate.parse("2025-12-02"), LocalDate.parse("2026-01-02")));

        assertThat(repository.listByUserOrderedByFiredAtDesc(owner, 1, 50)).hasSize(1);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static AlertFiring newFiring(
            UserId userId, Direction direction, String threshold, int windowDays, Instant firedAt,
            String valueStart, String valueEnd, String percentChange,
            LocalDate windowStartDate, LocalDate windowEndDate) {
        return new AlertFiring(
                new FiringId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                direction,
                new Percentage(new BigDecimal(threshold)),
                new AlertLookbackWindow(windowDays),
                firedAt,
                new Money(new BigDecimal(valueStart)),
                new Money(new BigDecimal(valueEnd)),
                new BigDecimal(percentChange),
                windowStartDate,
                windowEndDate);
    }
}
