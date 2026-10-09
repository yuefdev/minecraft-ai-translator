package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Redactor;

/**
 * A failed provider call. The message is redacted on construction so it is always safe to log or show.
 */
public final class ProviderException extends Exception {

    public enum Kind {
        NOT_CONFIGURED(false),
        AUTH(false),
        BAD_REQUEST(false),
        RATE_LIMIT(true),
        SERVER(true),
        TIMEOUT(true),
        NETWORK(true),
        BAD_RESPONSE(true),
        INTERRUPTED(false);

        final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;
    private final long retryAfterMillis;

    public ProviderException(Kind kind, String message) {
        this(kind, message, 0L);
    }

    public ProviderException(Kind kind, String message, long retryAfterMillis) {
        super(Redactor.redact(message), null, false, false);
        this.kind = kind;
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    public Kind kind() {
        return kind;
    }

    public boolean retryable() {
        return kind.retryable;
    }

    /** Server supplied wait time (Retry-After), or 0. */
    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
