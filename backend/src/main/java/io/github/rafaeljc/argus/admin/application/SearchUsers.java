package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import org.springframework.stereotype.Service;

@Service
public class SearchUsers {

    private final AdminUserQuery adminUserQuery;

    public SearchUsers(AdminUserQuery adminUserQuery) {
        this.adminUserQuery = adminUserQuery;
    }

    public PageResult<User> search(AdminUserSearchCriteria criteria, int page, int perPage) {
        return adminUserQuery.search(criteria, page, perPage);
    }
}
