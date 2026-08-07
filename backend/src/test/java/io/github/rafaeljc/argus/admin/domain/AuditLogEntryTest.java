package io.github.rafaeljc.argus.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AuditLogEntryTest {

    private static final AuditEntryId ID = new AuditEntryId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId ACTOR_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final UserId TARGET_ID = new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    private static final Instant CREATED_AT = Instant.parse("2026-06-15T21:00:00Z");

    @ParameterizedTest
    @EnumSource(value = AdminAction.class, names = {"SUSPEND", "UNSUSPEND", "DELETE"})
    void constructor_userTargetedActionWithTargetUser_isAllowed(AdminAction action) {
        AuditLogEntry entry = new AuditLogEntry(ID, ACTOR_ID, action, TARGET_ID, "{\"reason\":\"abuse\"}", CREATED_AT);

        assertThat(entry.id()).isEqualTo(ID);
        assertThat(entry.actorId()).isEqualTo(ACTOR_ID);
        assertThat(entry.action()).isEqualTo(action);
        assertThat(entry.targetUserId()).isEqualTo(TARGET_ID);
        assertThat(entry.metadataJson()).isEqualTo("{\"reason\":\"abuse\"}");
        assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = AdminAction.class, names = {"SUSPEND", "UNSUSPEND", "DELETE"})
    void constructor_userTargetedActionWithoutTargetUser_throwsIllegalArgument(AdminAction action) {
        assertThatThrownBy(() -> new AuditLogEntry(ID, ACTOR_ID, action, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetUserId");
    }

    @ParameterizedTest
    @EnumSource(value = AdminAction.class, names = {"EOD_RUN", "EOD_STEP_RERUN"})
    void constructor_pipelineActionWithoutTargetUser_isAllowed(AdminAction action) {
        AuditLogEntry entry = new AuditLogEntry(ID, ACTOR_ID, action, null, "{\"run_id\":\"abc\"}", CREATED_AT);

        assertThat(entry.targetUserId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AdminAction.class, names = {"EOD_RUN", "EOD_STEP_RERUN"})
    void constructor_pipelineActionWithTargetUser_isAllowed(AdminAction action) {
        AuditLogEntry entry = new AuditLogEntry(ID, ACTOR_ID, action, TARGET_ID, null, CREATED_AT);

        assertThat(entry.targetUserId()).isEqualTo(TARGET_ID);
    }

    @Test
    void constructor_nullMetadata_isAllowed() {
        AuditLogEntry entry = new AuditLogEntry(ID, ACTOR_ID, AdminAction.EOD_RUN, null, null, CREATED_AT);

        assertThat(entry.metadataJson()).isNull();
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntry(null, ACTOR_ID, AdminAction.EOD_RUN, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullActorId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntry(ID, null, AdminAction.EOD_RUN, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId");
    }

    @Test
    void constructor_nullAction_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntry(ID, ACTOR_ID, null, TARGET_ID, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntry(ID, ACTOR_ID, AdminAction.EOD_RUN, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }
}
