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
    private final SuspendUser suspendUser;
    private final UnsuspendUser unsuspendUser;
    private final DeleteUser deleteUser;
    private final ListAuditLog listAuditLog;

    public AdminService(SearchUsers searchUsers,
                        GetUser getUser,
                        SuspendUser suspendUser,
                        UnsuspendUser unsuspendUser,
                        DeleteUser deleteUser,
                        ListAuditLog listAuditLog) {
        this.searchUsers = searchUsers;
        this.getUser = getUser;
        this.suspendUser = suspendUser;
        this.unsuspendUser = unsuspendUser;
        this.deleteUser = deleteUser;
        this.listAuditLog = listAuditLog;
    }

    @Transactional(readOnly = true)
    public PageResult<User> searchUsers(AdminUserSearchCriteria criteria, int page, int perPage) {
        return searchUsers.search(criteria, page, perPage);
    }

    @Transactional(readOnly = true)
    public User getUser(UserId id) {
        return getUser.get(id);
    }

    @Transactional
    public User suspendUser(UserId targetId, UserId actorId, String reason) {
        return suspendUser.suspend(targetId, actorId, reason);
    }

    @Transactional
    public User unsuspendUser(UserId targetId, UserId actorId, String reason) {
        return unsuspendUser.unsuspend(targetId, actorId, reason);
    }

    @Transactional
    public User deleteUser(UserId targetId, UserId actorId, String reason) {
        return deleteUser.delete(targetId, actorId, reason);
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogEntryView> listAuditLog(AuditLogFilter filter, int page, int perPage) {
        return listAuditLog.list(filter, page, perPage);
    }
}
