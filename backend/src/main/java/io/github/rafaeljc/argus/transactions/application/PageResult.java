package io.github.rafaeljc.argus.transactions.application;

import java.util.List;

public record PageResult<T>(List<T> items, int total, int page, int perPage) {

    public PageResult {
        if (items == null) {
            throw new IllegalArgumentException("PageResult items must not be null");
        }
        if (total < 0) {
            throw new IllegalArgumentException("PageResult total must not be negative");
        }
        if (page < 1) {
            throw new IllegalArgumentException("PageResult page must be >= 1");
        }
        if (perPage < 1) {
            throw new IllegalArgumentException("PageResult perPage must be >= 1");
        }
        items = List.copyOf(items);
    }

    public int totalPages() {
        return total == 0 ? 0 : (total + perPage - 1) / perPage;
    }
}
