package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final SearchUsers searchUsers;
    private final GetUser getUser;

    public AdminService(SearchUsers searchUsers, GetUser getUser) {
        this.searchUsers = searchUsers;
        this.getUser = getUser;
    }

    @Transactional(readOnly = true)
    public PageResult<User> searchUsers(AdminUserSearchCriteria criteria, int page, int perPage) {
        return searchUsers.search(criteria, page, perPage);
    }

    @Transactional(readOnly = true)
    public User getUser(UserId id) {
        return getUser.get(id);
    }
}
