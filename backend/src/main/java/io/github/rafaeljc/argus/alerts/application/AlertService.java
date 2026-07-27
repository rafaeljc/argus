package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final CreateAlertRule createAlertRule;
    private final CancelAlertRule cancelAlertRule;

    public AlertService(CreateAlertRule createAlertRule, CancelAlertRule cancelAlertRule) {
        this.createAlertRule = createAlertRule;
        this.cancelAlertRule = cancelAlertRule;
    }

    @Transactional
    public AlertRule create(UserId userId, Direction direction, Percentage threshold, AlertLookbackWindow window) {
        return createAlertRule.create(userId, direction, threshold, window);
    }

    @Transactional
    public void cancel(UserId userId, RuleId id) {
        cancelAlertRule.cancel(userId, id);
    }
}
