package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import org.springframework.stereotype.Service;

@Service
public class GetUser {

    private final AdminUserQuery adminUserQuery;

    public GetUser(AdminUserQuery adminUserQuery) {
        this.adminUserQuery = adminUserQuery;
    }

    public User get(UserId id) {
        return adminUserQuery.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + id.value()));
    }
}
