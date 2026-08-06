package io.github.rafaeljc.argus.alerts.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.alerts.domain.TooManyAlertRulesException;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;

@Service
public class CreateAlertRule {

    private static final int MAX_ACTIVE_RULES = 20;

    private final AlertRuleRepository repository;
    private final TransactionalMutationLock lock;
    private final Clock clock;

    public CreateAlertRule(AlertRuleRepository repository, TransactionalMutationLock lock, Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.clock = clock;
    }

    public AlertRule create(UserId userId, Direction direction, Percentage threshold, AlertLookbackWindow window) {
        lock.acquireResourceById("alert-rule", userId.value());

        if (repository.countActiveByUser(userId) >= MAX_ACTIVE_RULES) {
            throw new TooManyAlertRulesException(MAX_ACTIVE_RULES);
        }

        AlertRule rule = new AlertRule(
                new RuleId(UuidCreator.getTimeOrderedEpoch()), userId, direction, threshold, window, clock.now());
        return repository.insert(rule);
    }
}
