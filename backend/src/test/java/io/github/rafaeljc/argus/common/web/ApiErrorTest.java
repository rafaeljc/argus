package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApiErrorTest {

    @Test
    void constructor_validArgs_assignsAllFields() {
        FieldError fe = new FieldError("quantity", "out_of_range", "must be > 0");

        ApiError error = new ApiError("VALIDATION_ERROR", "Request validation failed", List.of(fe));

        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.message()).isEqualTo("Request validation failed");
        assertThat(error.details()).containsExactly(fe);
    }

    @ParameterizedTest
    @CsvSource({
            ",     message",
            "CODE,        "
    })
    void constructor_nullComponent_throwsIllegalArgument(String code, String message) {
        assertThatThrownBy(() -> new ApiError(code, message, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullDetails_normalizedToEmptyList() {
        ApiError error = new ApiError("CODE", "message", null);

        assertThat(error.details()).isEqualTo(List.of());
    }

    @Test
    void constructor_mutableDetailsSource_defensivelyCopies() {
        List<FieldError> mutable = new ArrayList<>();
        mutable.add(new FieldError("a", "b", "c"));

        ApiError error = new ApiError("CODE", "message", mutable);
        mutable.clear();

        assertThat(error.details()).hasSize(1);
    }

    @Test
    void details_returnsUnmodifiableList() {
        ApiError error = new ApiError("CODE", "message", List.of(new FieldError("a", "b", "c")));

        assertThatThrownBy(() -> error.details().add(new FieldError("x", "y", "z")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
