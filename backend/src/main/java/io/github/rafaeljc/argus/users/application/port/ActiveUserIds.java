package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;

// Read-only facade for peer modules that need to scope work to active users (neither suspended
// nor soft-deleted) without reaching into UserRepository/UserService.
public interface ActiveUserIds {

    List<UserId> find();
}
