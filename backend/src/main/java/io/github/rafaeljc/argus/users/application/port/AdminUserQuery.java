package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.Optional;

public interface AdminUserQuery {

    PageResult<User> search(AdminUserSearchCriteria criteria, int page, int perPage);

    Optional<User> findById(UserId id);
}
