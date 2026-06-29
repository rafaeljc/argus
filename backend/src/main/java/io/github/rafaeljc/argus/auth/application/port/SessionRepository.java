package io.github.rafaeljc.argus.auth.application.port;

import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findByTokenHash(String sessionTokenHash);

    List<Session> findByUserId(UserId userId);
}
