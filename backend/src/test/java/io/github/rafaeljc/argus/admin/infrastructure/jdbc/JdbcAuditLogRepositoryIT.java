package io.github.rafaeljc.argus.admin.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.AuditLogEntryView;
import io.github.rafaeljc.argus.admin.application.AuditLogFilter;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcAuditLogRepositoryIT {

    private static final Instant CREATED_AT = Instant.parse("2026-06-15T21:00:00Z");

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void insert_userTargetedAction_persistsRowWithMetadataAsJsonb() {
        UserId actorId = newUser();
        UserId targetId = newUser();
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());

        repository.insert(new AuditLogEntry(
                id, actorId, AdminAction.SUSPEND, targetId, new AuditMetadata.UserAction("abuse"), CREATED_AT));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM admin_audit_log WHERE id = ?", id.value());
        assertThat(row.get("actor_id")).isEqualTo(actorId.value());
        assertThat(row.get("action")).isEqualTo("SUSPEND");
        assertThat(row.get("target_user_id")).isEqualTo(targetId.value());
        assertThat(row.get("metadata").toString()).isEqualTo("{\"reason\": \"abuse\"}");
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant()).isEqualTo(CREATED_AT);
    }

    @Test
    void insert_pipelineActionWithoutTargetUser_persistsNullTargetUserId() {
        UserId actorId = newUser();
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());

        repository.insert(new AuditLogEntry(
                id, actorId, AdminAction.EOD_RUN, null, null, CREATED_AT));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM admin_audit_log WHERE id = ?", id.value());
        assertThat(row.get("target_user_id")).isNull();
    }

    @Test
    void insert_nullMetadata_persistsNullMetadata() {
        UserId actorId = newUser();
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());

        repository.insert(new AuditLogEntry(id, actorId, AdminAction.EOD_RUN, null, null, CREATED_AT));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM admin_audit_log WHERE id = ?", id.value());
        assertThat(row.get("metadata")).isNull();
    }

    @Test
    void insert_unknownActorId_violatesForeignKey() {
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());
        UserId unknownActor = new UserId(UuidCreator.getTimeOrderedEpoch());

        assertThatThrownBy(() -> repository.insert(new AuditLogEntry(
                id, unknownActor, AdminAction.EOD_RUN, null, null, CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findFiltered_singleEntryNoFilters_returnsItWithFieldsMapped() {
        UserId actorId = newUser();
        UserId targetId = newUser();
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());
        repository.insert(new AuditLogEntry(
                id, actorId, AdminAction.SUSPEND, targetId, new AuditMetadata.UserAction("abuse"), CREATED_AT));

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, null, null), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        AuditLogEntryView entry = result.items().get(0);
        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.actorId()).isEqualTo(actorId);
        assertThat(entry.action()).isEqualTo(AdminAction.SUSPEND);
        assertThat(entry.targetUserId()).isEqualTo(targetId);
        assertThat(entry.metadata()).containsExactly(Map.entry("reason", "abuse"));
        assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void findFiltered_actorIdFilter_returnsOnlyMatchingActor() {
        UserId matchingActor = newUser();
        UserId otherActor = newUser();
        UserId targetId = newUser();
        insertEntry(matchingActor, AdminAction.SUSPEND, targetId, CREATED_AT);
        insertEntry(otherActor, AdminAction.SUSPEND, targetId, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(matchingActor, null, null, null, null), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).actorId()).isEqualTo(matchingActor);
    }

    @Test
    void findFiltered_targetUserIdFilter_returnsOnlyMatchingTarget() {
        UserId actorId = newUser();
        UserId matchingTarget = newUser();
        UserId otherTarget = newUser();
        insertEntry(actorId, AdminAction.SUSPEND, matchingTarget, CREATED_AT);
        insertEntry(actorId, AdminAction.SUSPEND, otherTarget, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, matchingTarget, null, null, null), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).targetUserId()).isEqualTo(matchingTarget);
    }

    @Test
    void findFiltered_actionFilter_returnsOnlyMatchingAction() {
        UserId actorId = newUser();
        UserId targetId = newUser();
        insertEntry(actorId, AdminAction.SUSPEND, targetId, CREATED_AT);
        insertEntry(actorId, AdminAction.UNSUSPEND, targetId, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, AdminAction.UNSUSPEND, null, null), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).action()).isEqualTo(AdminAction.UNSUSPEND);
    }

    @Test
    void findFiltered_dateRange_isInclusiveOnBothBoundaries() {
        UserId actorId = newUser();
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofDays(1)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.plus(Duration.ofDays(1)));

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, CREATED_AT, CREATED_AT), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void findFiltered_combinedFilters_appliesAllPredicates() {
        UserId matchingActor = newUser();
        UserId otherActor = newUser();
        UserId targetId = newUser();
        insertEntry(matchingActor, AdminAction.SUSPEND, targetId, CREATED_AT);
        insertEntry(matchingActor, AdminAction.UNSUSPEND, targetId, CREATED_AT);
        insertEntry(otherActor, AdminAction.SUSPEND, targetId, CREATED_AT);

        PageResult<AuditLogEntryView> result = repository.findFiltered(
                new AuditLogFilter(matchingActor, null, AdminAction.SUSPEND, null, null), 1, 50);

        assertThat(result.total()).isEqualTo(1);
        AuditLogEntryView entry = result.items().get(0);
        assertThat(entry.actorId()).isEqualTo(matchingActor);
        assertThat(entry.action()).isEqualTo(AdminAction.SUSPEND);
    }

    @Test
    void findFiltered_noMatches_returnsEmptyPageWithZeroTotal() {
        UserId actorId = newUser();
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);

        PageResult<AuditLogEntryView> result = repository.findFiltered(
                new AuditLogFilter(null, null, AdminAction.EOD_STEP_RERUN, null, null), 1, 50);

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void findFiltered_ordering_isCreatedAtDescThenIdDesc() {
        UserId actorId = newUser();
        AuditEntryId earlier = insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofDays(1)));
        AuditEntryId laterA = insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);
        AuditEntryId laterB = insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);
        AuditEntryId tieWinner = laterA.value().compareTo(laterB.value()) > 0 ? laterA : laterB;
        AuditEntryId tieLoser = tieWinner == laterA ? laterB : laterA;

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, null, null), 1, 50);

        List<AuditEntryId> ids = result.items().stream().map(AuditLogEntryView::id).toList();
        assertThat(ids).containsExactly(tieWinner, tieLoser, earlier);
    }

    @Test
    void findFiltered_pagination_limitsPageSizeAndComputesOffset() {
        UserId actorId = newUser();
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofSeconds(3)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofSeconds(2)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofSeconds(1)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, null, null), 2, 2);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).createdAt()).isEqualTo(CREATED_AT.minus(Duration.ofSeconds(2)));
        assertThat(result.items().get(1).createdAt()).isEqualTo(CREATED_AT.minus(Duration.ofSeconds(3)));
    }

    @Test
    void findFiltered_totalReflectsFilterNotPageSize() {
        UserId actorId = newUser();
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofSeconds(2)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT.minus(Duration.ofSeconds(1)));
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, null, null), 1, 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void findFiltered_nullMetadata_passesThroughAsNull() {
        UserId actorId = newUser();
        insertEntry(actorId, AdminAction.EOD_RUN, null, CREATED_AT);

        PageResult<AuditLogEntryView> result =
                repository.findFiltered(new AuditLogFilter(null, null, null, null, null), 1, 50);

        assertThat(result.items().get(0).metadata()).isNull();
    }

    private AuditEntryId insertEntry(UserId actorId, AdminAction action, UserId targetId, Instant createdAt) {
        AuditEntryId id = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());
        repository.insert(new AuditLogEntry(id, actorId, action, targetId, null, createdAt));
        return id;
    }

    private UserId newUser() {
        return userService.createUnverified(
                "admin-audit-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
