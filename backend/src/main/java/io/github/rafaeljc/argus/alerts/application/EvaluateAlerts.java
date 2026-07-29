package io.github.rafaeljc.argus.alerts.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import io.github.rafaeljc.argus.portfolio.application.GetSnapshot;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class EvaluateAlerts {

    private static final String LOCK_RESOURCE = "alert-eval";
    private static final int PERCENT_SCALE = 2;

    private final AlertRuleRepository ruleRepository;
    private final AlertFiringRepository firingRepository;
    private final GetSnapshot getSnapshot;
    private final MarketCalendar marketCalendar;
    private final EmailService emailService;
    private final UserService userService;
    private final TransactionalMutationLock lock;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public EvaluateAlerts(
            AlertRuleRepository ruleRepository,
            AlertFiringRepository firingRepository,
            GetSnapshot getSnapshot,
            MarketCalendar marketCalendar,
            EmailService emailService,
            UserService userService,
            TransactionalMutationLock lock,
            Clock clock,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.firingRepository = firingRepository;
        this.getSnapshot = getSnapshot;
        this.marketCalendar = marketCalendar;
        this.emailService = emailService;
        this.userService = userService;
        this.lock = lock;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public void forUser(UserId userId, LocalDate runDate) {
        lock.acquireResourceForUser(LOCK_RESOURCE, userId);

        Optional<PortfolioSnapshot> endSnapshot = getSnapshot.at(userId, runDate);
        if (endSnapshot.isEmpty()) {
            return;
        }
        PortfolioSnapshot end = endSnapshot.get();

        // Rules commonly share a window (only 7 distinct AlertLookbackWindow.days values exist),
        // so cache the resolved start snapshot per window length rather than re-querying the
        // market calendar and the portfolio-snapshot store once per rule.
        Map<Integer, Optional<PortfolioSnapshot>> startSnapshotsByWindowDays = new HashMap<>();
        List<AlertFiring> firings = new ArrayList<>();
        for (AlertRule rule : ruleRepository.listAllActiveByUser(userId)) {
            Optional<PortfolioSnapshot> startSnapshot = startSnapshotsByWindowDays.computeIfAbsent(
                    rule.window().days(), days -> resolveStartSnapshot(userId, runDate, days));
            startSnapshot.flatMap(start -> fire(userId, rule, start, end)).ifPresent(firings::add);
        }

        if (firings.isEmpty()) {
            return;
        }

        enqueueDigest(userId, runDate, firings);
    }

    private Optional<PortfolioSnapshot> resolveStartSnapshot(UserId userId, LocalDate runDate, int windowDays) {
        LocalDate windowStart = marketCalendar.mostRecentTradingDayOnOrBefore(runDate.minusDays(windowDays));
        return getSnapshot.at(userId, windowStart);
    }

    private Optional<AlertFiring> fire(
            UserId userId, AlertRule rule, PortfolioSnapshot startSnapshot, PortfolioSnapshot endSnapshot) {
        Money start = startSnapshot.totalValue();
        if (start.value().compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        Money end = endSnapshot.totalValue();
        BigDecimal percentChange = percentChange(start.value(), end.value());
        if (!crossesThreshold(rule, percentChange)) {
            return Optional.empty();
        }

        LocalDate windowStart = startSnapshot.snapshotDate();
        LocalDate windowEnd = endSnapshot.snapshotDate();
        return ruleRepository
                .deleteActiveAndReturn(rule.id(), userId)
                .map(deleted -> insertFiring(userId, deleted, start, end, percentChange, windowStart, windowEnd));
    }

    private AlertFiring insertFiring(
            UserId userId,
            AlertRule rule,
            Money start,
            Money end,
            BigDecimal percentChange,
            LocalDate windowStart,
            LocalDate windowEnd) {
        AlertFiring firing = new AlertFiring(
                new FiringId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                rule.id(),
                rule.direction(),
                rule.threshold(),
                rule.window(),
                clock.now(),
                start,
                end,
                percentChange,
                windowStart,
                windowEnd);
        return firingRepository.insert(firing);
    }

    private void enqueueDigest(UserId userId, LocalDate runDate, List<AlertFiring> firings) {
        User user = userService.lookup(userId);
        String serialized = objectMapper.writeValueAsString(digestPayload(userId, user.email(), runDate, firings));
        String idempotenceKey = "digest:" + userId.value() + ":" + runDate;
        emailService.enqueue(EventType.DIGEST, userId.value(), serialized, idempotenceKey);
    }

    private static boolean crossesThreshold(AlertRule rule, BigDecimal percentChange) {
        BigDecimal threshold = rule.threshold().value();
        return rule.direction() == Direction.UP
                ? percentChange.compareTo(threshold) >= 0
                : percentChange.compareTo(threshold.negate()) <= 0;
    }

    private static BigDecimal percentChange(BigDecimal start, BigDecimal end) {
        return end.subtract(start)
                .multiply(BigDecimal.valueOf(100))
                .divide(start, PERCENT_SCALE, RoundingMode.HALF_EVEN);
    }

    private static Map<String, Object> digestPayload(
            UserId userId, String email, LocalDate runDate, List<AlertFiring> firings) {
        List<Map<String, String>> firingLines = firings.stream()
                .map(EvaluateAlerts::firingLine)
                .toList();
        return Map.of(
                "user_id", userId.value().toString(),
                "email", email,
                "run_date", runDate.toString(),
                "firings", firingLines);
    }

    private static Map<String, String> firingLine(AlertFiring firing) {
        return Map.of(
                "rule_id", firing.ruleId().value().toString(),
                "direction", firing.direction().name(),
                "threshold", firing.threshold().value().toString(),
                "window_days", String.valueOf(firing.window().days()),
                "portfolio_value_start", firing.portfolioValueStart().value().toPlainString(),
                "portfolio_value_end", firing.portfolioValueEnd().value().toPlainString(),
                "percent_change", firing.percentChange().toString(),
                "window_start_date", firing.windowStartDate().toString(),
                "window_end_date", firing.windowEndDate().toString());
    }
}
