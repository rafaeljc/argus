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
    private static final UserId ACTOR_ID = new UserId(UUID.randomUUID());

    @Mock
    private SearchUsers searchUsers;

    @Mock
    private GetUser getUser;

    @Mock
    private SuspendUser suspendUser;

    @Mock
    private UnsuspendUser unsuspendUser;

    @Mock
    private DeleteUser deleteUser;

    @Mock
    private ListAuditLog listAuditLog;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(searchUsers, getUser, suspendUser, unsuspendUser, deleteUser, listAuditLog);
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

    @Test
    void suspendUser_delegatesToSuspendUserUseCase() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        User user = new User(USER_ID, "alice@example.com", "hash",
                true, true, false, false, now, now, null);
        when(suspendUser.suspend(USER_ID, ACTOR_ID, "abuse")).thenReturn(user);

        User result = service.suspendUser(USER_ID, ACTOR_ID, "abuse");

        assertThat(result).isEqualTo(user);
    }

    @Test
    void unsuspendUser_delegatesToUnsuspendUserUseCase() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        User user = new User(USER_ID, "alice@example.com", "hash",
                true, false, false, false, now, now, null);
        when(unsuspendUser.unsuspend(USER_ID, ACTOR_ID, "appeal")).thenReturn(user);

        User result = service.unsuspendUser(USER_ID, ACTOR_ID, "appeal");

        assertThat(result).isEqualTo(user);
    }

    @Test
    void deleteUser_delegatesToDeleteUserUseCase() {
        User user = new User(USER_ID, "alice@example.com", "hash",
                true, false, true, false, Instant.parse("2026-08-07T00:00:00Z"),
                Instant.parse("2026-08-07T00:00:00Z"), Instant.parse("2026-08-07T00:00:00Z"));
        when(deleteUser.delete(USER_ID, ACTOR_ID, "policy")).thenReturn(user);

        User result = service.deleteUser(USER_ID, ACTOR_ID, "policy");

        assertThat(result).isEqualTo(user);
    }

    @Test
    void listAuditLog_delegatesToListAuditLogUseCase() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        PageResult<AuditLogEntryView> expected = new PageResult<>(List.of(), 0, 1, 50);
        when(listAuditLog.list(filter, 1, 50)).thenReturn(expected);

        PageResult<AuditLogEntryView> result = service.listAuditLog(filter, 1, 50);

        assertThat(result).isSameAs(expected);
    }
}
