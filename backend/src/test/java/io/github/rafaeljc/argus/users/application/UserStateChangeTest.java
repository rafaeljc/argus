package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserStateChangeTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");

    private static User user() {
        UserId id = new UserId(UuidCreator.getTimeOrderedEpoch());
        return new User(id, "alice@example.com", "$argon2id$v=19$m=65536,t=3,p=1$encoded",
                true, false, false, false, NOW, NOW, null);
    }

    @Test
    void constructor_nullUser_throwsIllegalArgument() {
        assertThatThrownBy(() -> new UserStateChange(null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validUser_storesUserAndChangedFlag() {
        User user = user();

        UserStateChange result = new UserStateChange(user, true);

        assertThat(result.user()).isSameAs(user);
        assertThat(result.changed()).isTrue();
    }
}
