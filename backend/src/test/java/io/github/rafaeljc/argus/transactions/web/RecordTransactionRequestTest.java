package io.github.rafaeljc.argus.transactions.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.transactions.domain.Operation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordTransactionRequestTest {

    private static final BigDecimal QUANTITY = new BigDecimal("10");
    private static final LocalDate TRADE_DATE = LocalDate.parse("2026-06-15");

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_allFieldsValid_noViolations() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", Operation.BUY, QUANTITY, TRADE_DATE);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void validate_blankTicker_violatesNotBlank() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("", Operation.BUY, QUANTITY, TRADE_DATE);

        assertThat(violatedProperties(request)).contains("ticker");
    }

    @Test
    void validate_lowercaseTicker_violatesPattern() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("aapl", Operation.BUY, QUANTITY, TRADE_DATE);

        assertThat(violatedProperties(request)).contains("ticker");
    }

    @Test
    void validate_nullOperation_violatesNotNull() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", null, QUANTITY, TRADE_DATE);

        assertThat(violatedProperties(request)).contains("operation");
    }

    @Test
    void validate_zeroQuantity_violatesPositive() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", Operation.BUY, BigDecimal.ZERO, TRADE_DATE);

        assertThat(violatedProperties(request)).contains("quantity");
    }

    @Test
    void validate_negativeQuantity_violatesPositive() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", Operation.BUY, new BigDecimal("-1"), TRADE_DATE);

        assertThat(violatedProperties(request)).contains("quantity");
    }

    @Test
    void validate_quantityWithTooManyFractionDigits_violatesDigits() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", Operation.BUY, new BigDecimal("1.1234567"), TRADE_DATE);

        assertThat(violatedProperties(request)).contains("quantity");
    }

    @Test
    void validate_nullTradeDate_violatesNotNull() {
        RecordTransactionRequest request =
                new RecordTransactionRequest("AAPL", Operation.BUY, QUANTITY, null);

        assertThat(violatedProperties(request)).contains("tradeDate");
    }

    private static Set<String> violatedProperties(RecordTransactionRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
