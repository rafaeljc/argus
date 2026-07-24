package io.github.rafaeljc.argus.alerts.application.port;

import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface AlertFiringRepository {

    AlertFiring insert(AlertFiring firing);

    Optional<AlertFiring> findByIdAndUser(FiringId id, UserId userId);

    List<AlertFiring> listByUserOrderedByFiredAtDesc(UserId userId, int page, int perPage);
}
