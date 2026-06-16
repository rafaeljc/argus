package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldErrorTest {

    @Test
    void constructor_allFieldsProvided_stored() {
        FieldError fe = new FieldError("trade_date", "out_of_range", "must be <= today");

        assertThat(fe.field()).isEqualTo("trade_date");
        assertThat(fe.code()).isEqualTo("out_of_range");
        assertThat(fe.message()).isEqualTo("must be <= today");
    }

    @ParameterizedTest
    @CsvSource({
            ",            code, message",
            "field,       ,     message",
            "field,       code,        "
    })
    void constructor_nullComponent_throwsIllegalArgument(String field, String code, String message) {
        assertThatThrownBy(() -> new FieldError(field, code, message))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
