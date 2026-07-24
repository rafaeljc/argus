package io.github.rafaeljc.argus.alerts.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.alerts.domain.DuplicateAlertRuleException;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcAlertRuleRepositoryIT {

    private static final Instant RULE_CREATED = Instant.parse("2026-06-22T12:00:00Z");

    @Autowired
    private AlertRuleRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void insert_thenFindActiveByIdAndUser_returnsPersistedRule() {
        UserId userId = newUser();
        AlertRule saved = repository.insert(
                newRule(userId, Direction.UP, "5.0", 30, RULE_CREATED));

        Optional<AlertRule> loaded = repository.findActiveByIdAndUser(saved.id(), userId);

        assertThat(loaded).isPresent();
        AlertRule rule = loaded.get();
        assertThat(rule.id()).isEqualTo(saved.id());
        assertThat(rule.userId()).isEqualTo(userId);
        assertThat(rule.direction()).isEqualTo(Direction.UP);
        assertThat(rule.threshold()).isEqualTo(new Percentage(new BigDecimal("5.0")));
        assertThat(rule.window()).isEqualTo(new AlertLookbackWindow(30));
        assertThat(rule.createdAt()).isEqualTo(RULE_CREATED);
    }

    @Test
    void findActiveByIdAndUser_unknownId_returnsEmpty() {
        UserId userId = newUser();
        RuleId unknown = new RuleId(UuidCreator.getTimeOrderedEpoch());

        assertThat(repository.findActiveByIdAndUser(unknown, userId)).isEmpty();
    }

    @Test
    void findActiveByIdAndUser_differentOwner_returnsEmpty() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        AlertRule saved = repository.insert(newRule(owner, Direction.UP, "5.0", 30, RULE_CREATED));

        assertThat(repository.findActiveByIdAndUser(saved.id(), otherUser)).isEmpty();
    }

    @Test
    void countActiveByUser_matchesNumberOfOwnedRules() {
        UserId userId = newUser();
        UserId otherUser = newUser();
        repository.insert(newRule(userId, Direction.UP, "5.0", 30, RULE_CREATED));
        repository.insert(newRule(userId, Direction.DOWN, "10.0", 90, RULE_CREATED));
        repository.insert(newRule(otherUser, Direction.UP, "5.0", 30, RULE_CREATED));

        assertThat(repository.countActiveByUser(userId)).isEqualTo(2);
    }

    @Test
    void countActiveByUser_noRules_returnsZero() {
        UserId userId = newUser();

        assertThat(repository.countActiveByUser(userId)).isZero();
    }

    @Test
    void countActiveByUser_twentyDistinctSignatureRules_returnsTwenty() {
        UserId userId = newUser();
        for (int i = 1; i <= 20; i++) {
            repository.insert(newRule(userId, Direction.UP, i + ".0", 30, RULE_CREATED));
        }

        assertThat(repository.countActiveByUser(userId)).isEqualTo(20);
    }

    @Test
    void listActiveByUserOrderedByCreatedAtDesc_ordersNewestFirst() {
        UserId userId = newUser();
        AlertRule oldest = repository.insert(
                newRule(userId, Direction.UP, "1.0", 30, Instant.parse("2026-01-01T00:00:00Z")));
        AlertRule newest = repository.insert(
                newRule(userId, Direction.UP, "3.0", 30, Instant.parse("2026-03-01T00:00:00Z")));
        AlertRule middle = repository.insert(
                newRule(userId, Direction.UP, "2.0", 30, Instant.parse("2026-02-01T00:00:00Z")));

        List<AlertRule> page = repository.listActiveByUserOrderedByCreatedAtDesc(userId, 1, 50);

        assertThat(page).extracting(AlertRule::id).containsExactly(newest.id(), middle.id(), oldest.id());
    }

    @Test
    void listActiveByUserOrderedByCreatedAtDesc_pagination_slicesCorrectly() {
        UserId userId = newUser();
        AlertRule first = repository.insert(
                newRule(userId, Direction.UP, "1.0", 30, Instant.parse("2026-01-01T00:00:00Z")));
        AlertRule second = repository.insert(
                newRule(userId, Direction.UP, "2.0", 30, Instant.parse("2026-02-01T00:00:00Z")));
        AlertRule third = repository.insert(
                newRule(userId, Direction.UP, "3.0", 30, Instant.parse("2026-03-01T00:00:00Z")));

        List<AlertRule> pageOne = repository.listActiveByUserOrderedByCreatedAtDesc(userId, 1, 2);
        List<AlertRule> pageTwo = repository.listActiveByUserOrderedByCreatedAtDesc(userId, 2, 2);

        assertThat(pageOne).extracting(AlertRule::id).containsExactly(third.id(), second.id());
        assertThat(pageTwo).extracting(AlertRule::id).containsExactly(first.id());
    }

    @Test
    void listActiveByUserOrderedByCreatedAtDesc_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.insert(newRule(owner, Direction.UP, "5.0", 30, RULE_CREATED));
        repository.insert(newRule(otherUser, Direction.UP, "5.0", 30, RULE_CREATED));

        assertThat(repository.listActiveByUserOrderedByCreatedAtDesc(owner, 1, 50)).hasSize(1);
    }

    @Test
    void deleteActiveAndReturn_ownedRule_removesAndReturnsRule() {
        UserId userId = newUser();
        AlertRule saved = repository.insert(newRule(userId, Direction.UP, "5.0", 30, RULE_CREATED));

        Optional<AlertRule> deleted = repository.deleteActiveAndReturn(saved.id(), userId);

        assertThat(deleted).isPresent();
        assertThat(deleted.get().id()).isEqualTo(saved.id());
        assertThat(repository.findActiveByIdAndUser(saved.id(), userId)).isEmpty();
    }

    @Test
    void deleteActiveAndReturn_differentOwner_returnsEmptyAndKeepsRow() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        AlertRule saved = repository.insert(newRule(owner, Direction.UP, "5.0", 30, RULE_CREATED));

        Optional<AlertRule> deleted = repository.deleteActiveAndReturn(saved.id(), otherUser);

        assertThat(deleted).isEmpty();
        assertThat(repository.findActiveByIdAndUser(saved.id(), owner)).isPresent();
    }

    @Test
    void deleteActiveAndReturn_unknownId_returnsEmpty() {
        UserId userId = newUser();
        RuleId unknown = new RuleId(UuidCreator.getTimeOrderedEpoch());

        assertThat(repository.deleteActiveAndReturn(unknown, userId)).isEmpty();
    }

    @Test
    void insert_duplicateSignatureForSameUser_throwsDuplicateAlertRuleException() {
        UserId userId = newUser();
        repository.insert(newRule(userId, Direction.UP, "5.0", 30, RULE_CREATED));

        assertThatThrownBy(() -> repository.insert(newRule(userId, Direction.UP, "5.0", 30, RULE_CREATED)))
                .isInstanceOf(DuplicateAlertRuleException.class);
    }

    @Test
    void insert_sameSignatureDifferentUser_isAllowed() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.insert(newRule(owner, Direction.UP, "5.0", 30, RULE_CREATED));

        assertThat(repository.insert(newRule(otherUser, Direction.UP, "5.0", 30, RULE_CREATED)))
                .isNotNull();
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static AlertRule newRule(
            UserId userId, Direction direction, String threshold, int windowDays, Instant createdAt) {
        return new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                direction,
                new Percentage(new BigDecimal(threshold)),
                new AlertLookbackWindow(windowDays),
                createdAt);
    }
}
