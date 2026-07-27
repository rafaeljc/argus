package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;

@Service
public class CancelAlertRule {

    private final AlertRuleRepository repository;

    public CancelAlertRule(AlertRuleRepository repository) {
        this.repository = repository;
    }

    public void cancel(UserId userId, RuleId id) {
        repository
                .deleteActiveAndReturn(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("alert rule not found: " + id.value()));
    }
}
