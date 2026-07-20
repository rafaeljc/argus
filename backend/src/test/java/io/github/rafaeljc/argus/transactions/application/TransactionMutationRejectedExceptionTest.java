package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionMutationRejectedExceptionTest {

    private static final List<FieldError> FIELD_ERRORS = List.of(
            new FieldError("trade_date", "would_invalidate_sell", "sell #4821 on 2026-03-10 would be oversold"),
            new FieldError("trade_date", "would_invalidate_sell", "sell #4977 on 2026-04-02 would be oversold"));

    @Test
    void exposesWireCodeAndStatus() {
        TransactionMutationRejectedException ex = new TransactionMutationRejectedException(FIELD_ERRORS);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.status()).isEqualTo(422);
    }

    @Test
    void detailsReturnsSuppliedFieldErrors() {
        TransactionMutationRejectedException ex = new TransactionMutationRejectedException(FIELD_ERRORS);

        assertThat(ex.details()).isEqualTo(FIELD_ERRORS);
    }

    @Test
    void detailsIsImmutableCopyOfConstructorArgument() {
        List<FieldError> mutable = new ArrayList<>(FIELD_ERRORS);
        TransactionMutationRejectedException ex = new TransactionMutationRejectedException(mutable);
        mutable.clear();

        assertThat(ex.details()).hasSize(2);
        assertThatThrownBy(() -> ex.details().add(FIELD_ERRORS.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
