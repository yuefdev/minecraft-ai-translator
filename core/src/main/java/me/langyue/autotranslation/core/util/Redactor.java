package me.langyue.autotranslation.core.util;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Removes credentials from any text before it is logged or shown in the UI.
 * Registered secrets are removed verbatim; in addition well known token shapes are masked so that a key that
 * is echoed back by a server (or was typed with different whitespace) is still hidden.
 */
public final class Redactor {
    private static final Set<String> REGISTERED = new CopyOnWriteArraySet<>();
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{6,}");
    private static final Pattern SK_TOKEN = Pattern.compile("\\b(sk|rk|pk)-[A-Za-z0-9_-]{6,}");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(authorization|x-api-key|api-key)(\"?\\s*[:=]\\s*\"?)[^\\s\",}]+");
    private static final Pattern URL_KEY_PARAM = Pattern.compile("(?i)([?&](?:api[_-]?key|key|token)=)[^&\\s]+");
    /** Google (AIza..., and the newer "AQ." keys of AI Studio) and Groq (gsk_...) key shapes. */
    private static final Pattern PROVIDER_KEYS = Pattern.compile("\\b(AIza|AQ\\.|gsk_)[A-Za-z0-9._-]{8,}");

    private Redactor() {
    }

    /** Remember a credential so that {@link #redact(String)} always removes it. */
    public static void register(Secret secret) {
        if (secret != null && !secret.isEmpty() && secret.reveal().length() >= 4) {
            REGISTERED.add(secret.reveal());
        }
    }

    public static void clearRegistered() {
        REGISTERED.clear();
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (String secret : REGISTERED) {
            result = result.replace(secret, "****");
        }
        result = BEARER.matcher(result).replaceAll("$1****");
        result = AUTH_HEADER.matcher(result).replaceAll("$1$2****");
        result = URL_KEY_PARAM.matcher(result).replaceAll("$1****");
        result = SK_TOKEN.matcher(result).replaceAll("$1-****");
        result = PROVIDER_KEYS.matcher(result).replaceAll("$1****");
        return result;
    }

    public static String redact(Throwable throwable) {
        if (throwable == null) return "";
        String message = throwable.getClass().getSimpleName() + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        return redact(message);
    }
}
