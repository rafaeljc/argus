package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;

@Service
public class GetActiveRule {

    private final AlertRuleRepository repository;

    public GetActiveRule(AlertRuleRepository repository) {
        this.repository = repository;
    }

    public AlertRule get(UserId userId, RuleId id) {
        return repository.findActiveByIdAndUser(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("alert rule not found: " + id.value()));
    }
}
