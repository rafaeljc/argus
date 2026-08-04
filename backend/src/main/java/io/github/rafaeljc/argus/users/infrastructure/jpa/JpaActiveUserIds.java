package io.github.rafaeljc.argus.users.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaActiveUserIds implements ActiveUserIds {

    private final SpringDataUserJpaRepository jpa;

    JpaActiveUserIds(SpringDataUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<UserId> find() {
        return jpa.findByDeletedFalseAndSuspendedFalse().stream()
                .map(entity -> new UserId(entity.getId()))
                .toList();
    }
}
