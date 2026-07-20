package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.List;

public final class TransactionMutationRejectedException extends DomainException {

    private final List<FieldError> fieldErrors;

    public TransactionMutationRejectedException(List<FieldError> fieldErrors) {
        super("transaction mutation would invalidate later sells");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    @Override
    public String code() {
        return "VALIDATION_ERROR";
    }

    @Override
    public List<FieldError> details() {
        return fieldErrors;
    }
}
