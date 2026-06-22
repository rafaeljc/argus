package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidCurrentPasswordExceptionTest {

    @Test
    void exposesWireCodeStatusAndStructuredFields() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        InvalidCurrentPasswordException ex = new InvalidCurrentPasswordException(userId);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("INVALID_CURRENT_PASSWORD");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.userId()).isEqualTo(userId);
    }
}
