package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Secret;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ready-made translation services, so that a player only has to pick one (or just paste a key). Every preset that is
 * not "custom" brings its own address, model and the request limits of its free tier; "auto" picks the service from
 * the key that was entered and falls back to the keyless machine translation when there is no key.
 */
public final class ProviderPreset {
    public enum Kind {
        /** Any {@code /chat/completions} endpoint: hosted LLM services, local Ollama / LM Studio. */
        OPENAI_COMPATIBLE,
        /** Google Translate's public web endpoint: no key, no account, plain machine translation. */
        GOOGLE_FREE
    }

    public static final String AUTO = "auto";
    public static final String CUSTOM = "custom";
    public static final String GOOGLE_FREE = "google-free";
    public static final String GEMINI = "gemini";
    public static final String GROQ = "groq";
    public static final String MISTRAL = "mistral";
    public static final String OPENROUTER = "openrouter";
    public static final String OLLAMA = "ollama";

    private final String id;
    private final Kind kind;
    private final String baseUrl;
    private final String model;
    private final int requestsPerMinute;
    private final boolean keyRequired;
    private final String keyPrefix;
    private final String keyUrl;
    private final int maxBatchChars;
    private final int maxSegmentsPerBatch;
    private final Map<String, String> extraParameters;
    private final List<String> fallbackModels;
    private final boolean sendTemperature;

    private ProviderPreset(String id, Kind kind, String baseUrl, String model, int requestsPerMinute, boolean keyRequired,
                           String keyPrefix, String keyUrl, int maxBatchChars, int maxSegmentsPerBatch,
                           Map<String, String> extraParameters, List<String> fallbackModels, boolean sendTemperature) {
        this.id = id;
        this.kind = kind;
        this.baseUrl = baseUrl;
        this.model = model;
        this.requestsPerMinute = requestsPerMinute;
        this.keyRequired = keyRequired;
        this.keyPrefix = keyPrefix;
        this.keyUrl = keyUrl;
        this.maxBatchChars = maxBatchChars;
        this.maxSegmentsPerBatch = maxSegmentsPerBatch;
        this.extraParameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(extraParameters));
        this.fallbackModels = Collections.unmodifiableList(new ArrayList<String>(fallbackModels));
        this.sendTemperature = sendTemperature;
    }

    private static List<String> models(String... ids) {
        List<String> list = new ArrayList<String>();
        Collections.addAll(list, ids);
        return list;
    }

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);
        return map;
    }

    private static final List<ProviderPreset> ALL;

    /*
     * Free-tier facts as of October 2026 (limits are per account and change without notice; the request limits below
     * stay a little under them). Gemini: gemini-2.5 models are closed to new users since 2026-09-18, Gemini 3 models
     * always "think" (the lowest level is "minimal") and Google advises leaving the temperature at its default.
     * Groq: Llama 3.3 left the free plan on 2026-08-16; gpt-oss reasons always, "low" keeps it short; the binding
     * limit is 8,000 tokens per minute. OpenRouter: ":free" models rotate; 50 requests per day without bought
     * credits, and free endpoints must be allowed in the account's privacy settings. Mistral: free "Experiment" plan
     * (phone verification), generous volume. Each model has its own daily quota, so the fallbacks add capacity.
     */
    static {
        List<ProviderPreset> all = new ArrayList<ProviderPreset>();
        all.add(new ProviderPreset(GOOGLE_FREE, Kind.GOOGLE_FREE, "https://translate.googleapis.com/translate_a/t", "gtx",
                20, false, "", "", 5000, 50, params(), models(), true));
        all.add(new ProviderPreset(GEMINI, Kind.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.5-flash-lite", 10, true, "AIza", "https://aistudio.google.com/apikey", 6000, 48,
                params("reasoning_effort", "minimal"), models("gemini-3.1-flash-lite"), false));
        all.add(new ProviderPreset(GROQ, Kind.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1",
                "openai/gpt-oss-120b", 4, true, "gsk_", "https://console.groq.com/keys", 2500, 24,
                params("reasoning_effort", "low"), models("openai/gpt-oss-20b"), true));
        all.add(new ProviderPreset(MISTRAL, Kind.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1",
                "mistral-medium-latest", 50, true, "", "https://console.mistral.ai/api-keys", 6000, 48,
                params(), models("mistral-small-latest"), true));
        all.add(new ProviderPreset(OPENROUTER, Kind.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1",
                "google/gemma-4-31b-it:free", 15, true, "sk-or-", "https://openrouter.ai/keys", 6000, 48,
                params(), models("google/gemma-4-26b-a4b-it:free"), true));
        all.add(new ProviderPreset(OLLAMA, Kind.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "gemma3:4b",
                600, false, "", "https://ollama.com/download", 2000, 16, params(), models(), true));
        ALL = Collections.unmodifiableList(all);
    }

    /** All selectable presets in display order (without "auto" and "custom", which are not services). */
    public static List<ProviderPreset> all() {
        return ALL;
    }

    /** All ids a settings file or screen may contain, in display order. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<String>();
        ids.add(AUTO);
        for (ProviderPreset p : ALL) ids.add(p.id);
        ids.add(CUSTOM);
        return ids;
    }

    /** @return the preset, or null for "auto", "custom" and unknown ids */
    public static ProviderPreset byId(String id) {
        if (id == null) return null;
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (ProviderPreset p : ALL) {
            if (p.id.equals(wanted)) return p;
        }
        return null;
    }

    /**
     * Which service an API key belongs to, judged by the prefix that each service puts on its keys.
     *
     * @return the preset, or null when the key is empty or unknown (for example an OpenAI key)
     */
    public static ProviderPreset forKey(Secret key) {
        if (key == null || key.isEmpty()) return null;
        String value = key.reveal().trim();
        ProviderPreset best = null;
        for (ProviderPreset p : ALL) {
            if (!p.keyPrefix.isEmpty() && value.startsWith(p.keyPrefix)
                    && (best == null || p.keyPrefix.length() > best.keyPrefix.length())) {
                best = p;
            }
        }
        return best;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    /** Requests per minute that the free tier allows (a little below the documented limit). */
    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public boolean keyRequired() {
        return keyRequired;
    }

    /** Where a player gets a (free) key, or where the program is downloaded; empty if not applicable. */
    public String keyUrl() {
        return keyUrl;
    }

    public int maxBatchChars() {
        return maxBatchChars;
    }

    public int maxSegmentsPerBatch() {
        return maxSegmentsPerBatch;
    }

    /** Extra request fields for this service (for example keeping a model's "thinking" short). */
    public Map<String, String> extraParameters() {
        return extraParameters;
    }

    /** Models of the same service with their own daily quota, used in turn when one is used up or withdrawn. */
    public List<String> fallbackModels() {
        return fallbackModels;
    }

    /** False where the service advises against changing the sampling temperature. */
    public boolean sendTemperature() {
        return sendTemperature;
    }

    @Override
    public String toString() {
        return "ProviderPreset[" + id + "]";
    }
}
