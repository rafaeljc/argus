package io.github.rafaeljc.argus.users.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JpaActiveUserIdsIT {

    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Autowired
    private ActiveUserIds activeUserIds;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void find_noUsers_returnsEmpty() {
        assertThat(activeUserIds.find()).isEmpty();
    }

    @Test
    void find_activeUser_isIncluded() {
        UserId id = newUser("alice@example.com");

        assertThat(activeUserIds.find()).contains(id);
    }

    @Test
    void find_suspendedUser_isExcluded() {
        UserId id = newUser("bob@example.com");
        suspend(id);

        assertThat(activeUserIds.find()).doesNotContain(id);
    }

    @Test
    void find_softDeletedUser_isExcluded() {
        UserId id = newUser("carol@example.com");
        userService.softDelete(id, RAW_PASSWORD);

        assertThat(activeUserIds.find()).doesNotContain(id);
    }

    private UserId newUser(String email) {
        return userService.createUnverified(email, RAW_PASSWORD).id();
    }

    private void suspend(UserId id) {
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", id.value());
    }
}
