package io.github.rafaeljc.argus.email.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventTypeTest {

    @Test
    void dbValue_verification_matchesSchemaCheck() {
        assertThat(EventType.VERIFICATION.dbValue()).isEqualTo("email.verification");
    }

    @Test
    void dbValue_passwordReset_matchesSchemaCheck() {
        assertThat(EventType.PASSWORD_RESET.dbValue()).isEqualTo("email.password_reset");
    }

    @Test
    void dbValue_digest_matchesSchemaCheck() {
        assertThat(EventType.DIGEST.dbValue()).isEqualTo("email.digest");
    }

    @Test
    void fromDbValue_knownValue_returnsConstant() {
        assertThat(EventType.fromDbValue("email.verification")).isEqualTo(EventType.VERIFICATION);
        assertThat(EventType.fromDbValue("email.password_reset")).isEqualTo(EventType.PASSWORD_RESET);
        assertThat(EventType.fromDbValue("email.digest")).isEqualTo(EventType.DIGEST);
    }

    @Test
    void fromDbValue_unknownValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> EventType.fromDbValue("email.unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email.unknown");
    }
}
