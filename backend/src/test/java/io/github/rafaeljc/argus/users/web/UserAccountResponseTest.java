package io.github.rafaeljc.argus.users.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountResponseTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T12:34:56Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-15T08:00:00Z");
    private static final String PASSWORD_HASH = "$argon2id$v=19$m=65536,t=3,p=4$abc$def";

    @Test
    void from_projectsContractFields_andOmitsSensitiveOnes() {
        User user = new User(
                new UserId(ID),
                "alice@example.com",
                PASSWORD_HASH,
                true,   // verified
                false,  // suspended
                false,  // deleted
                true,   // admin
                CREATED_AT, UPDATED_AT, null);

        UserAccountResponse response = UserAccountResponse.from(user);

        assertThat(response.id()).isEqualTo(ID);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.isVerified()).isTrue();
        assertThat(response.isAdmin()).isTrue();
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }
}
