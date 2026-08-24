package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public interface AdminAssignment {

    int makeSoleAdmin(UserId adminId, Instant now);
}
