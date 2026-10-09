package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.Secret;

/**
 * Connection settings of an OpenAI-compatible chat completions endpoint (OpenAI, OpenRouter, LM Studio,
 * Ollama's /v1, ...). The API key is a {@link Secret} and never appears in {@code toString()}.
 */
public final class OpenAiSettings {
    private final String baseUrl;
    private final String model;
    private final Secret apiKey;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final double temperature;
    private final int maxOutputTokens;
    private final boolean jsonResponseFormat;
    private final java.util.Map<String, String> extraParameters;
    private final java.util.List<String> fallbackModels;

    public OpenAiSettings(String baseUrl, String model, Secret apiKey, int connectTimeoutMillis, int requestTimeoutMillis, double temperature, int maxOutputTokens, boolean jsonResponseFormat) {
        this(baseUrl, model, apiKey, connectTimeoutMillis, requestTimeoutMillis, temperature, maxOutputTokens, jsonResponseFormat,
                java.util.Collections.<String, String>emptyMap());
    }

    /**
     * @param extraParameters additional top-level request fields with string values (for example
     *                        {@code reasoning_effort=none}); dropped one by one if the endpoint refuses them
     */
    public OpenAiSettings(String baseUrl, String model, Secret apiKey, int connectTimeoutMillis, int requestTimeoutMillis, double temperature, int maxOutputTokens, boolean jsonResponseFormat,
                          java.util.Map<String, String> extraParameters) {
        this(baseUrl, model, apiKey, connectTimeoutMillis, requestTimeoutMillis, temperature, maxOutputTokens, jsonResponseFormat,
                extraParameters, java.util.Collections.<String>emptyList());
    }

    /**
     * @param temperature    {@link Double#NaN} leaves the field out (the service's own default is used)
     * @param fallbackModels further models of the same service, used in turn when a model's daily quota is used up
     *                       or the model was withdrawn
     */
    public OpenAiSettings(String baseUrl, String model, Secret apiKey, int connectTimeoutMillis, int requestTimeoutMillis, double temperature, int maxOutputTokens, boolean jsonResponseFormat,
                          java.util.Map<String, String> extraParameters, java.util.List<String> fallbackModels) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.jsonResponseFormat = jsonResponseFormat;
        this.extraParameters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>(
                extraParameters == null ? java.util.Collections.<String, String>emptyMap() : extraParameters));
        this.fallbackModels = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(
                fallbackModels == null ? java.util.Collections.<String>emptyList() : fallbackModels));
    }

    public java.util.List<String> fallbackModels() {
        return fallbackModels;
    }

    public java.util.Map<String, String> extraParameters() {
        return extraParameters;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public Secret apiKey() {
        return apiKey;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int requestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public double temperature() {
        return temperature;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public boolean jsonResponseFormat() {
        return jsonResponseFormat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiSettings other = (OpenAiSettings) o;
        return java.util.Objects.equals(baseUrl, other.baseUrl) && java.util.Objects.equals(model, other.model) && java.util.Objects.equals(apiKey, other.apiKey) && connectTimeoutMillis == other.connectTimeoutMillis && requestTimeoutMillis == other.requestTimeoutMillis && temperature == other.temperature && maxOutputTokens == other.maxOutputTokens && jsonResponseFormat == other.jsonResponseFormat && extraParameters.equals(other.extraParameters) && fallbackModels.equals(other.fallbackModels);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(baseUrl, model, apiKey, connectTimeoutMillis, requestTimeoutMillis, temperature, maxOutputTokens, jsonResponseFormat, extraParameters, fallbackModels);
    }

    public static OpenAiSettings defaults() {
        return new OpenAiSettings("https://api.openai.com/v1", "gpt-4o-mini", Secret.empty(),
                10_000, 45_000, 0.2, 4096, true);
    }

    public OpenAiSettings withModel(String newModel) {
        return new OpenAiSettings(baseUrl, newModel, apiKey, connectTimeoutMillis, requestTimeoutMillis,
                temperature, maxOutputTokens, jsonResponseFormat, extraParameters, fallbackModels);
    }

    public OpenAiSettings withApiKey(Secret key) {
        return new OpenAiSettings(baseUrl, model, key, connectTimeoutMillis, requestTimeoutMillis,
                temperature, maxOutputTokens, jsonResponseFormat, extraParameters, fallbackModels);
    }

    /** Full URL of the chat completions endpoint. */
    public String endpoint() {
        String url = baseUrl == null ? "" : Compat.strip(baseUrl);
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
        if (baseUrl == null || Compat.isBlank(baseUrl) || model == null || Compat.isBlank(model)) return false;
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
