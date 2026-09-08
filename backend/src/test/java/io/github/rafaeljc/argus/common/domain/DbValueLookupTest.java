package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DbValueLookupTest {

    @ParameterizedTest
    @CsvSource({
            "one,ONE",
            "two,TWO"
    })
    void get_knownValue_returnsMatchingConstant(String dbValue, Sample expected) {
        DbValueLookup<Sample> lookup = DbValueLookup.of(Sample.values(), "sample");

        assertThat(lookup.get(dbValue)).isEqualTo(expected);
    }

    @Test
    void get_null_throwsIllegalArgument() {
        DbValueLookup<Sample> lookup = DbValueLookup.of(Sample.values(), "sample");

        assertThatThrownBy(() -> lookup.get(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void get_unknown_throwsIllegalArgumentNamingLabelAndValue() {
        DbValueLookup<Sample> lookup = DbValueLookup.of(Sample.values(), "sample");

        assertThatThrownBy(() -> lookup.get("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sample")
                .hasMessageContaining("bogus");
    }

    @Test
    void get_isCaseSensitive() {
        DbValueLookup<Sample> lookup = DbValueLookup.of(Sample.values(), "sample");

        assertThatThrownBy(() -> lookup.get("ONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_blankDbValue_throwsIllegalState() {
        assertThatThrownBy(() -> DbValueLookup.of(BlankValue.values(), "blank_value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank_value")
                .hasMessageContaining("BLANK");
    }

    @Test
    void of_nullDbValue_throwsIllegalState() {
        assertThatThrownBy(() -> DbValueLookup.of(NullValue.values(), "null_value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null_value")
                .hasMessageContaining("NULL");
    }

    @Test
    void of_duplicateDbValue_throwsIllegalState() {
        assertThatThrownBy(() -> DbValueLookup.of(DuplicateValue.values(), "duplicate_value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate_value")
                .hasMessageContaining("dup");
    }

    private enum Sample implements DbValued {

        ONE("one"),
        TWO("two");

        private final String dbValue;

        Sample(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }
    }

    private enum BlankValue implements DbValued {

        BLANK(""),
        FINE("fine");

        private final String dbValue;

        BlankValue(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }
    }

    private enum NullValue implements DbValued {

        NULL(null),
        FINE("fine");

        private final String dbValue;

        NullValue(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }
    }

    private enum DuplicateValue implements DbValued {

        FIRST("dup"),
        SECOND("dup");

        private final String dbValue;

        DuplicateValue(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }
    }
}
