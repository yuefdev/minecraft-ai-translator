package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Secret;

/**
 * Connection settings of an OpenAI-compatible chat completions endpoint (OpenAI, OpenRouter, LM Studio,
 * Ollama's /v1, ...). The API key is a {@link Secret} and never appears in {@code toString()}.
 */
public record OpenAiSettings(
        String baseUrl,
        String model,
        Secret apiKey,
        int connectTimeoutMillis,
        int requestTimeoutMillis,
        double temperature,
        int maxOutputTokens,
        boolean jsonResponseFormat
) {
    public static OpenAiSettings defaults() {
        return new OpenAiSettings("https://api.openai.com/v1", "gpt-4o-mini", Secret.empty(),
                10_000, 45_000, 0.2, 4096, true);
    }

    public OpenAiSettings withModel(String newModel) {
        return new OpenAiSettings(baseUrl, newModel, apiKey, connectTimeoutMillis, requestTimeoutMillis,
                temperature, maxOutputTokens, jsonResponseFormat);
    }

    public OpenAiSettings withApiKey(Secret key) {
        return new OpenAiSettings(baseUrl, model, key, connectTimeoutMillis, requestTimeoutMillis,
                temperature, maxOutputTokens, jsonResponseFormat);
    }

    /** Full URL of the chat completions endpoint. */
    public String endpoint() {
        String url = baseUrl == null ? "" : baseUrl.strip();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    /** Host used to identify the provider in cache keys (never contains credentials or paths). */
    public String host() {
        try {
            java.net.URI uri = java.net.URI.create(endpoint());
            String host = uri.getHost();
            if (host == null) return "unknown";
            return uri.getPort() == -1 ? host : host + ":" + uri.getPort();
        } catch (IllegalArgumentException e) {
            return "invalid";
        }
    }

    public boolean looksConfigured() {
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) return false;
        String lower = baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        String bareHost = host().replaceFirst(":\\d+$", "");
        boolean local = bareHost.equals("localhost") || bareHost.equals("127.0.0.1") || bareHost.equals("[::1]");
        // hosted services need a key; local servers (Ollama, LM Studio) usually do not
        return local || !apiKey.isEmpty();
    }

    @Override
    public String toString() {
        return "OpenAiSettings[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + apiKey + "]";
    }
}
