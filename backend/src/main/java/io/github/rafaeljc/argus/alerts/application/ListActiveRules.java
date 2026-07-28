package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;

@Service
public class ListActiveRules {

    private final AlertRuleRepository repository;

    public ListActiveRules(AlertRuleRepository repository) {
        this.repository = repository;
    }

    public PageResult<AlertRule> list(UserId userId, int page, int perPage) {
        return new PageResult<>(
                repository.listActiveByUserOrderedByCreatedAtDesc(userId, page, perPage),
                repository.countActiveByUser(userId),
                page,
                perPage);
    }
}
