package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.List;

public interface AdminAssignment {

    // Returns every user id whose is_admin flag actually changed (the newly granted admin, plus
    // any previous admin demoted alongside it), so callers can react to demotions as well as the
    // grant itself.
    List<UserId> makeSoleAdmin(UserId adminId, Instant now);
}
