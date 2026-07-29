package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final CreateAlertRule createAlertRule;
    private final CancelAlertRule cancelAlertRule;
    private final ListActiveRules listActiveRules;
    private final GetActiveRule getActiveRule;
    private final ListFirings listFirings;
    private final EvaluateAlerts evaluateAlerts;

    public AlertService(
            CreateAlertRule createAlertRule,
            CancelAlertRule cancelAlertRule,
            ListActiveRules listActiveRules,
            GetActiveRule getActiveRule,
            ListFirings listFirings,
            EvaluateAlerts evaluateAlerts) {
        this.createAlertRule = createAlertRule;
        this.cancelAlertRule = cancelAlertRule;
        this.listActiveRules = listActiveRules;
        this.getActiveRule = getActiveRule;
        this.listFirings = listFirings;
        this.evaluateAlerts = evaluateAlerts;
    }

    @Transactional
    public AlertRule create(UserId userId, Direction direction, Percentage threshold, AlertLookbackWindow window) {
        return createAlertRule.create(userId, direction, threshold, window);
    }

    @Transactional
    public void cancel(UserId userId, RuleId id) {
        cancelAlertRule.cancel(userId, id);
    }

    @Transactional(readOnly = true)
    public PageResult<AlertRule> listRules(UserId userId, int page, int perPage) {
        return listActiveRules.list(userId, page, perPage);
    }

    @Transactional(readOnly = true)
    public AlertRule getRule(UserId userId, RuleId id) {
        return getActiveRule.get(userId, id);
    }

    @Transactional(readOnly = true)
    public PageResult<AlertFiring> listFirings(UserId userId, int page, int perPage) {
        return listFirings.list(userId, page, perPage);
    }

    @Transactional
    public void evaluateForUser(UserId userId, LocalDate runDate) {
        evaluateAlerts.forUser(userId, runDate);
    }
}
