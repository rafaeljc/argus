package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the SignUp transaction contract: user, verification token and outbox row are
 * written atomically. If the outbox enqueue fails, nothing else survives — no orphan
 * user rows, no orphan verification tokens. This is what makes it safe to treat
 * "signup returned 201" as "the verification email will be sent."
 */
@Import(PostgresContainer.class)
@SpringBootTest
class SignUpTransactionRollbackIT {

    private static final String EMAIL = "rollback-it@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private SignUp signUp;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void execute_outboxEnqueueFails_rollsBackUserAndVerificationInsertsAtomically() {
        RuntimeException failure = new RuntimeException("simulated vendor-side enqueue failure");
        doThrow(failure).when(emailService).enqueue(any(), any(), any(), any());

        assertThatThrownBy(() -> signUp.execute(EMAIL, PASSWORD)).isSameAs(failure);

        assertRowCount("users", 0);
        assertRowCount("email_verifications", 0);
        assertRowCount("outbox", 0);
    }

    private void assertRowCount(String table, int expected) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        assertThat(count).as("row count in %s", table).isEqualTo(expected);
    }
}
