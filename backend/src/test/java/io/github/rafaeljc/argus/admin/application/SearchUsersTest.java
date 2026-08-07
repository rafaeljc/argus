package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchUsersTest {

    @Mock
    private AdminUserQuery adminUserQuery;

    private SearchUsers searchUsers;

    @BeforeEach
    void setUp() {
        searchUsers = new SearchUsers(adminUserQuery);
    }

    @Test
    void search_delegatesCriteriaAndPagingToPort_returnsPortResultUnchanged() {
        AdminUserSearchCriteria criteria = new AdminUserSearchCriteria("acme", true, false, null);
        PageResult<User> expected = new PageResult<>(List.of(), 0, 2, 25);
        when(adminUserQuery.search(criteria, 2, 25)).thenReturn(expected);

        PageResult<User> result = searchUsers.search(criteria, 2, 25);

        assertThat(result).isSameAs(expected);
    }
}
