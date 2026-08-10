package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogEntryViewTest {

    private static final AuditEntryId ID = new AuditEntryId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId ACTOR_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Instant CREATED_AT = Instant.parse("2026-06-15T21:00:00Z");

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntryView(null, ACTOR_ID, AdminAction.EOD_RUN, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullActorId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntryView(ID, null, AdminAction.EOD_RUN, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId");
    }

    @Test
    void constructor_nullAction_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntryView(ID, ACTOR_ID, null, null, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogEntryView(ID, ACTOR_ID, AdminAction.EOD_RUN, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_nullTargetUserIdAndMetadata_isAllowed() {
        AuditLogEntryView view = new AuditLogEntryView(ID, ACTOR_ID, AdminAction.EOD_RUN, null, null, CREATED_AT);

        assertThat(view.targetUserId()).isNull();
        assertThat(view.metadata()).isNull();
    }

    @Test
    void constructor_allFieldsPresent_retainsValues() {
        UserId targetId = new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        Map<String, Object> metadata = Map.of("reason", "abuse");

        AuditLogEntryView view =
                new AuditLogEntryView(ID, ACTOR_ID, AdminAction.SUSPEND, targetId, metadata, CREATED_AT);

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.actorId()).isEqualTo(ACTOR_ID);
        assertThat(view.action()).isEqualTo(AdminAction.SUSPEND);
        assertThat(view.targetUserId()).isEqualTo(targetId);
        assertThat(view.metadata()).isEqualTo(metadata);
        assertThat(view.createdAt()).isEqualTo(CREATED_AT);
    }
}
