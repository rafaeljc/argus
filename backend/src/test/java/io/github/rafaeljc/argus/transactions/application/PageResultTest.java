package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void totalPages_totalIsZero_returnsZero() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 1, 50);

        assertThat(result.totalPages()).isZero();
    }

    @Test
    void totalPages_totalIsExactMultipleOfPerPage_returnsExactQuotient() {
        PageResult<String> result = new PageResult<>(List.of(), 100, 1, 50);

        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void totalPages_totalHasRemainder_roundsUp() {
        PageResult<String> result = new PageResult<>(List.of(), 101, 1, 50);

        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void constructor_nullItems_throws() {
        assertThatThrownBy(() -> new PageResult<>(null, 0, 1, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeTotal_throws() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), -1, 1, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_pageLessThanOne_throws() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_perPageLessThanOne_throws() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_itemsIsDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("a"));
        PageResult<String> result = new PageResult<>(mutable, 1, 1, 50);

        mutable.add("b");

        assertThat(result.items()).containsExactly("a");
    }
}
