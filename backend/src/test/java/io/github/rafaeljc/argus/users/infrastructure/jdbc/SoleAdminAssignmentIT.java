package io.github.rafaeljc.argus.users.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.EnsureSoleAdmin;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.AdminAssignment;
import io.github.rafaeljc.argus.users.domain.EmailNotVerifiedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class SoleAdminAssignmentIT {

    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Autowired
    private AdminAssignment adminAssignment;

    @Autowired
    private EnsureSoleAdmin ensureSoleAdmin;

    @Autowired
    private UserService userService;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId verifiedUser(String email) {
        User created = userService.createUnverified(email, RAW_PASSWORD);
        return userService.markVerified(created.id()).id();
    }

    private UserId existingAdmin(String email) {
        UserId id = verifiedUser(email);
        jdbcTemplate.update("UPDATE users SET is_admin = TRUE WHERE id = ?", id.value());
        return id;
    }

    private List<UUID> adminIds() {
        return jdbcTemplate.queryForList("SELECT id FROM users WHERE is_admin = TRUE", UUID.class);
    }

    @Test
    void execute_otherAdminsExist_leavesTargetAsTheOnlyAdmin() {
        existingAdmin("previous@example.com");
        existingAdmin("stale@example.com");
        UserId target = verifiedUser("alice@example.com");

        ensureSoleAdmin.execute(target);

        assertThat(adminIds()).containsExactly(target.value());
    }

    @Test
    void makeSoleAdmin_reapplied_changesNoRows() {
        UserId target = verifiedUser("alice@example.com");
        existingAdmin("previous@example.com");

        int first = adminAssignment.makeSoleAdmin(target, clock.now());
        int second = adminAssignment.makeSoleAdmin(target, clock.now());

        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(adminIds()).containsExactly(target.value());
    }

    // The demotion sweep is deliberately unrestricted, so the post-condition is the strong global
    // one: exactly one row carries the flag, whatever state the other rows are in.
    @Test
    void makeSoleAdmin_softDeletedAdminExists_demotesItToo() {
        UserId departed = existingAdmin("departed@example.com");
        userService.softDelete(departed, RAW_PASSWORD);
        UserId target = verifiedUser("alice@example.com");

        adminAssignment.makeSoleAdmin(target, clock.now());

        assertThat(adminIds()).containsExactly(target.value());
    }

    @Test
    void makeSoleAdmin_targetGranted_bumpsUpdatedAt() {
        UserId target = verifiedUser("alice@example.com");
        Instant before = userService.lookup(target).updatedAt();

        adminAssignment.makeSoleAdmin(target, clock.now().plusSeconds(60));

        assertThat(userService.lookup(target).updatedAt()).isAfter(before);
    }

    // A rolling deploy runs the old and the new task at once, so two instances genuinely race here.
    // Postgres row locks serialise them: the second re-evaluates the predicate after the lock and
    // matches nothing, so either interleaving converges on the same state.
    @Test
    void makeSoleAdmin_concurrentInstances_convergeOnASingleAdmin() throws Exception {
        UserId target = verifiedUser("alice@example.com");
        existingAdmin("previous@example.com");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Instant now = clock.now();

        Runnable attempt = () -> {
            try {
                start.await();
                adminAssignment.makeSoleAdmin(target, now);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };
        Thread first = new Thread(attempt);
        Thread second = new Thread(attempt);
        first.start();
        second.start();

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        first.join();
        second.join();

        assertThat(failure.get()).isNull();
        assertThat(adminIds()).containsExactly(target.value());
    }

    @Test
    void execute_unverifiedTarget_leavesExistingAdminUntouched() {
        UserId incumbent = existingAdmin("previous@example.com");
        UserId unverified = userService.createUnverified("alice@example.com", RAW_PASSWORD).id();

        assertThatThrownBy(() -> ensureSoleAdmin.execute(unverified))
                .isInstanceOf(EmailNotVerifiedException.class);

        assertThat(adminIds()).containsExactly(incumbent.value());
    }
}
