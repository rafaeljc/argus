package io.github.rafaeljc.argus.common.domain;

import java.util.HashMap;
import java.util.Map;

public final class DbValueLookup<E extends Enum<E> & DbValued> {

    private final String label;
    private final Map<String, E> byDbValue;

    private DbValueLookup(E[] constants, String label) {
        this.label = label;
        this.byDbValue = indexByDbValue(constants, label);
    }

    public static <E extends Enum<E> & DbValued> DbValueLookup<E> of(E[] constants, String label) {
        return new DbValueLookup<>(constants, label);
    }

    public E get(String dbValue) {
        E constant = dbValue == null ? null : byDbValue.get(dbValue);
        if (constant == null) {
            throw new IllegalArgumentException("unknown " + label + ": " + dbValue);
        }
        return constant;
    }

    private static <E extends Enum<E> & DbValued> Map<String, E> indexByDbValue(E[] constants, String label) {
        Map<String, E> index = new HashMap<>();
        for (E constant : constants) {
            String dbValue = constant.dbValue();
            if (dbValue == null || dbValue.isBlank()) {
                throw new IllegalStateException(label + " " + constant.name() + " dbValue must not be blank");
            }
            E existing = index.put(dbValue, constant);
            if (existing != null) {
                throw new IllegalStateException(
                        label + " dbValue \"" + dbValue + "\" is used by both " + existing.name()
                                + " and " + constant.name());
            }
        }
        return Map.copyOf(index);
    }
}
