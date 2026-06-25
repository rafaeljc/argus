package io.github.rafaeljc.argus.users.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findActiveById(UserId id);

    Optional<User> findActiveByEmail(String email);

    User save(User user);
}
