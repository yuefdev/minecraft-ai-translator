package me.langyue.autotranslation.core.util;

import java.util.function.BiConsumer;

/**
 * Tiny logging facade so that the core has no dependency on Minecraft's logger. The mod points the sink at its
 * SLF4J logger; every message goes through {@link Redactor} first so credentials can never reach a log.
 */
public final class CoreLog {
    public enum Level {DEBUG, INFO, WARN, ERROR}

    public interface Sink {
        void log(Level level, String message, Throwable error);
    }

    private static final java.util.logging.Logger FALLBACK = java.util.logging.Logger.getLogger("AutoTranslation");
    private static volatile Sink sink = (level, message, error) -> {
        java.util.logging.Level l;
        switch (level) {
            case DEBUG:
                l = java.util.logging.Level.FINE;
                break;
            case INFO:
                l = java.util.logging.Level.INFO;
                break;
            case WARN:
                l = java.util.logging.Level.WARNING;
                break;
            default:
                l = java.util.logging.Level.SEVERE;
                break;
        }
        FALLBACK.log(l, message, error);
    };
    private static volatile boolean debug = false;

    private CoreLog() {
    }

    public static void setSink(Sink newSink) {
        sink = newSink == null ? sink : newSink;
    }

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static void debug(String message) {
        if (debug) log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void warn(String message, Throwable error) {
        log(Level.WARN, message, error);
    }

    public static void error(String message, Throwable error) {
        log(Level.ERROR, message, error);
    }

    private static void log(Level level, String message, Throwable error) {
        String safe = Redactor.redact(message);
        Throwable safeError = error == null ? null : new RedactedException(error);
        try {
            sink.log(level, safe, safeError);
        } catch (RuntimeException ignored) {
            // logging must never break translation
        }
    }

    /** Wraps an exception so that its message (which may echo a server response) is redacted. */
    private static final class RedactedException extends RuntimeException {
        RedactedException(Throwable cause) {
            super(Redactor.redact(cause), null, false, true);
            setStackTrace(cause.getStackTrace());
        }
    }
}
