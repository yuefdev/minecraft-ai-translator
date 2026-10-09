package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Secret;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * The translation service that is actually used, worked out from the chosen preset, the key and the custom fields.
 *
 * <ul>
 *     <li>{@code auto} (default): a key of a known free service (Gemini {@code AIza…}, Groq {@code gsk_…}, OpenRouter
 *     {@code sk-or-…}) selects that service; any other key uses the custom address and model (for example OpenAI);
 *     without a key the custom address is used if it points to this computer (Ollama, LM Studio), otherwise the
 *     keyless Google machine translation.</li>
 *     <li>a preset id: that service with its own address, model and free-tier limits.</li>
 *     <li>{@code custom}: exactly the address and model from the settings.</li>
 * </ul>
 */
public final class ProviderSelection {
    private static final int CUSTOM_BATCH_CHARS = 3000;
    private static final int CUSTOM_BATCH_SEGMENTS = 24;

    private final String presetId;
    private final ProviderPreset.Kind kind;
    private final String baseUrl;
    private final String model;
    private final int requestsPerMinute;
    private final int maxBatchChars;
    private final int maxSegmentsPerBatch;
    private final Map<String, String> extraParameters;
    private final java.util.List<String> fallbackModels;
    private final boolean sendTemperature;

    private ProviderSelection(String presetId, ProviderPreset.Kind kind, String baseUrl, String model, int requestsPerMinute,
                              int maxBatchChars, int maxSegmentsPerBatch, Map<String, String> extraParameters,
                              java.util.List<String> fallbackModels, boolean sendTemperature) {
        this.presetId = presetId;
        this.kind = kind;
        this.baseUrl = baseUrl;
        this.model = model;
        this.requestsPerMinute = requestsPerMinute;
        this.maxBatchChars = maxBatchChars;
        this.maxSegmentsPerBatch = maxSegmentsPerBatch;
        this.extraParameters = extraParameters;
        this.fallbackModels = fallbackModels;
        this.sendTemperature = sendTemperature;
    }

    /**
     * @param chosen            the preset id from the settings ("auto", "custom" or a preset id)
     * @param key               the API key (may be empty)
     * @param customEndpoint    the address from the settings, used by "custom"
     * @param customModel       the model from the settings, used by "custom"
     * @param requestsPerMinute the limit from the settings; a free preset never goes above its own limit
     */
    public static ProviderSelection resolve(String chosen, Secret key, String customEndpoint, String customModel,
                                            int requestsPerMinute) {
        String id = chosen == null ? ProviderPreset.AUTO : chosen.trim().toLowerCase(Locale.ROOT);
        int rpm = Math.max(1, requestsPerMinute);
        if (ProviderPreset.CUSTOM.equals(id)) return custom(customEndpoint, customModel, rpm);
        ProviderPreset preset = ProviderPreset.byId(id);
        if (preset == null) {
            // auto (or an id this version does not know)
            preset = ProviderPreset.forKey(key);
            if (preset == null) {
                if (key != null && !key.isEmpty()) return custom(customEndpoint, customModel, rpm);
                if (isLocal(customEndpoint)) return custom(customEndpoint, customModel, rpm);
                preset = ProviderPreset.byId(ProviderPreset.GOOGLE_FREE);
            }
        }
        return new ProviderSelection(preset.id(), preset.kind(), preset.baseUrl(), preset.model(),
                Math.min(rpm, preset.requestsPerMinute()), preset.maxBatchChars(), preset.maxSegmentsPerBatch(),
                preset.extraParameters(), preset.fallbackModels(), preset.sendTemperature());
    }

    private static ProviderSelection custom(String endpoint, String model, int rpm) {
        return new ProviderSelection(ProviderPreset.CUSTOM, ProviderPreset.Kind.OPENAI_COMPATIBLE,
                endpoint == null ? "" : endpoint.trim(), model == null ? "" : model.trim(), rpm,
                CUSTOM_BATCH_CHARS, CUSTOM_BATCH_SEGMENTS, Collections.<String, String>emptyMap(),
                Collections.<String>emptyList(), true);
    }

    static boolean isLocal(String endpoint) {
        if (endpoint == null) return false;
        String lower = endpoint.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://localhost") || lower.startsWith("http://127.0.0.1") || lower.startsWith("http://[::1]");
    }

    /** The preset in use ("custom" when the custom fields are used). */
    public String presetId() {
        return presetId;
    }

    public ProviderPreset.Kind kind() {
        return kind;
    }

    public boolean usesKeylessMachineTranslation() {
        return kind == ProviderPreset.Kind.GOOGLE_FREE;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public int maxBatchChars() {
        return maxBatchChars;
    }

    public int maxSegmentsPerBatch() {
        return maxSegmentsPerBatch;
    }

    public Map<String, String> extraParameters() {
        return extraParameters;
    }

    public java.util.List<String> fallbackModels() {
        return fallbackModels;
    }

    public boolean sendTemperature() {
        return sendTemperature;
    }

    @Override
    public String toString() {
        return "ProviderSelection[" + presetId + ", " + baseUrl + ", " + model + ", rpm=" + requestsPerMinute + "]";
    }
}
