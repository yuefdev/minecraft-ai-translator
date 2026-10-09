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

    private static final System.Logger FALLBACK = System.getLogger("AutoTranslation");
    private static volatile Sink sink = (level, message, error) -> {
        System.Logger.Level l = switch (level) {
            case DEBUG -> System.Logger.Level.DEBUG;
            case INFO -> System.Logger.Level.INFO;
            case WARN -> System.Logger.Level.WARNING;
            case ERROR -> System.Logger.Level.ERROR;
        };
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
