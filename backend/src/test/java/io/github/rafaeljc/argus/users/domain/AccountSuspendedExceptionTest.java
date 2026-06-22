package io.github.rafaeljc.argus.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountSuspendedExceptionTest {

    @Test
    void exposesWireCodeStatusAndStructuredFields() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        String email = "alice@example.com";

        AccountSuspendedException ex = new AccountSuspendedException(userId, email);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(ex.status()).isEqualTo(403);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.userId()).isEqualTo(userId);
        assertThat(ex.email()).isEqualTo(email);
    }
}
