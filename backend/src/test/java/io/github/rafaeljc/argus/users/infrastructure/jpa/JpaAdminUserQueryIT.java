package io.github.rafaeljc.argus.users.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JpaAdminUserQueryIT {

    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final AdminUserSearchCriteria NO_FILTERS = new AdminUserSearchCriteria(null, null, null, null);

    @Autowired
    private AdminUserQuery adminUserQuery;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void search_noFilters_returnsAllUsersIncludingSuspendedAndDeleted() {
        UserId active = newUser("alice@example.com");
        UserId suspended = newUser("bob@example.com");
        suspend(suspended);
        UserId deleted = newUser("carol@example.com");
        userService.softDelete(deleted, RAW_PASSWORD);

        PageResult<User> result = adminUserQuery.search(NO_FILTERS, 1, 50);

        assertThat(result.items()).extracting(User::id).containsExactlyInAnyOrder(active, suspended, deleted);
    }

    @Test
    void search_isSuspendedTrue_returnsOnlySuspended() {
        UserId suspended = newUser("dave@example.com");
        suspend(suspended);
        newUser("erin@example.com");

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria(null, true, null, null), 1, 50);

        assertThat(result.items()).extracting(User::id).containsExactly(suspended);
    }

    @Test
    void search_isSuspendedFalse_excludesSuspended() {
        newUser("frank@example.com");
        UserId suspended = newUser("grace@example.com");
        suspend(suspended);

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria(null, false, null, null), 1, 50);

        assertThat(result.items()).extracting(User::id).doesNotContain(suspended);
    }

    @Test
    void search_isDeletedTrue_returnsOnlyDeleted() {
        UserId deleted = newUser("heidi@example.com");
        userService.softDelete(deleted, RAW_PASSWORD);
        newUser("ivan@example.com");

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria(null, null, true, null), 1, 50);

        assertThat(result.items()).extracting(User::id).containsExactly(deleted);
    }

    @Test
    void search_isVerifiedTrue_returnsOnlyVerified() {
        User unverified = userService.createUnverified("judy@example.com", RAW_PASSWORD);
        User verified = userService.createUnverified("mallory@example.com", RAW_PASSWORD);
        userService.markVerified(verified.id());

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria(null, null, null, true), 1, 50);

        assertThat(result.items()).extracting(User::id)
                .contains(verified.id())
                .doesNotContain(unverified.id());
    }

    @Test
    void search_combinedFilters_appliesAllPredicates() {
        UserId matches = newUser("oscar@example.com");
        suspend(matches);
        UserId wrongState = newUser("oscar2@example.com");
        newUser("peggy@example.com");

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria("oscar", true, false, null), 1, 50);

        assertThat(result.items()).extracting(User::id).containsExactly(matches);
        assertThat(result.items()).extracting(User::id).doesNotContain(wrongState);
    }

    @Test
    void search_emailContainsFragment_isCaseInsensitivePartialMatch() {
        UserId target = newUser("jane.doe@ACME.com");
        newUser("someone-else@example.com");

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria("acme", null, null, null), 1, 50);

        assertThat(result.items()).extracting(User::id).containsExactly(target);
    }

    @Test
    void search_emailContainsWildcardCharacters_treatedAsLiterals() {
        newUser("a%b@example.com");
        UserId unrelated = newUser("plain@example.com");

        PageResult<User> result = adminUserQuery.search(
                new AdminUserSearchCriteria("a%b", null, null, null), 1, 50);

        assertThat(result.items()).extracting(User::id).doesNotContain(unrelated);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void search_pagination_slicesAndOrdersByCreatedAtDescending() {
        UserId oldest = newUser("first@example.com");
        UserId middle = newUser("second@example.com");
        UserId newest = newUser("third@example.com");

        PageResult<User> firstPage = adminUserQuery.search(NO_FILTERS, 1, 2);
        PageResult<User> secondPage = adminUserQuery.search(NO_FILTERS, 2, 2);

        assertThat(firstPage.items()).extracting(User::id).containsExactly(newest, middle);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(secondPage.items()).extracting(User::id).containsExactly(oldest);
    }

    @Test
    void findById_existingUser_returnsUserRegardlessOfState() {
        UserId deleted = newUser("romeo@example.com");
        userService.softDelete(deleted, RAW_PASSWORD);

        Optional<User> result = adminUserQuery.findById(deleted);

        assertThat(result).isPresent();
        assertThat(result.get().isDeleted()).isTrue();
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        Optional<User> result = adminUserQuery.findById(new UserId(UUID.randomUUID()));

        assertThat(result).isEmpty();
    }

    private UserId newUser(String email) {
        return userService.createUnverified(email, RAW_PASSWORD).id();
    }

    private void suspend(UserId id) {
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", id.value());
    }
}
