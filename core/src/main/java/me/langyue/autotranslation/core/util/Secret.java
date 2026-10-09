package me.langyue.autotranslation.core.util;

import java.util.Objects;

/**
 * Holds a credential so that it can never end up in a log line or a debug dump by accident:
 * {@link #toString()} is always masked and the raw value must be requested explicitly.
 */
public final class Secret {
    private static final Secret EMPTY = new Secret("");

    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    public static Secret of(String value) {
        if (value == null) return EMPTY;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? EMPTY : new Secret(trimmed);
    }

    public static Secret empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    /** The raw credential. Only call this when building the outgoing request. */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return value.isEmpty() ? "<unset>" : "****";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Secret)) return false;
        return Objects.equals(value, ((Secret) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
