package io.github.rafaeljc.argus.admin.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.time.Instant;
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

    private UserId newUser() {
        return userService.createUnverified(
                "admin-audit-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
