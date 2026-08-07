package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserTest {

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private AdminUserQuery adminUserQuery;

    private GetUser getUser;

    @BeforeEach
    void setUp() {
        getUser = new GetUser(adminUserQuery);
    }

    @Test
    void get_found_returnsUser() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        User user = new User(USER_ID, "alice@example.com", "hash",
                true, false, false, false, now, now, null);
        when(adminUserQuery.findById(USER_ID)).thenReturn(Optional.of(user));

        User result = getUser.get(USER_ID);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void get_missing_throwsResourceNotFound() {
        when(adminUserQuery.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUser.get(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
