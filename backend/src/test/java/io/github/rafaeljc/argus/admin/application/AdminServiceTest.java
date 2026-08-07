package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @Mock
    private SearchUsers searchUsers;

    @Mock
    private GetUser getUser;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(searchUsers, getUser);
    }

    @Test
    void searchUsers_delegatesToSearchUsersUseCase() {
        AdminUserSearchCriteria criteria = new AdminUserSearchCriteria("acme", null, null, null);
        PageResult<User> expected = new PageResult<>(List.of(), 0, 1, 50);
        when(searchUsers.search(criteria, 1, 50)).thenReturn(expected);

        PageResult<User> result = service.searchUsers(criteria, 1, 50);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getUser_delegatesToGetUserUseCase() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        User user = new User(USER_ID, "alice@example.com", "hash",
                true, false, false, false, now, now, null);
        when(getUser.get(USER_ID)).thenReturn(user);

        User result = service.getUser(USER_ID);

        assertThat(result).isEqualTo(user);
    }
}
