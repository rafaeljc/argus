package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserStateChange;

public interface UserLifecycle {

    UserStateChange suspend(UserId id);

    UserStateChange unsuspend(UserId id);

    UserStateChange softDelete(UserId id);
}
