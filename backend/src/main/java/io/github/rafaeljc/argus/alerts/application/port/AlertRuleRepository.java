package io.github.rafaeljc.argus.alerts.application.port;

import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository {

    AlertRule insert(AlertRule rule);

    int countActiveByUser(UserId userId);

    Optional<AlertRule> findActiveByIdAndUser(RuleId id, UserId userId);

    List<AlertRule> listActiveByUserOrderedByCreatedAtDesc(UserId userId, int page, int perPage);

    Optional<AlertRule> deleteActiveAndReturn(RuleId id, UserId userId);
}
