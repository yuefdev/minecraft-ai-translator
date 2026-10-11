package me.langyue.autotranslation.engine;

/**
 * User settings of the translator. Public mutable fields so that any configuration UI (Cloth Config, Forge's
 * config GUI, a file editor) can bind to them. The API key is intentionally <em>not</em> part of this class: it
 * lives in {@code secrets/api-key.txt} or the {@code AUTOTRANSLATION_API_KEY} environment variable.
 */
public class EngineSettings {
    public boolean enabled = true;
    public boolean translateQuests = true;
    public boolean translateBooks = true;
    public boolean translateGeneric = true;
    public boolean showOriginal = false;
    public boolean pauseWhileEditingQuests = true;
    public boolean bulkLanguageFiles = false;

    /**
     * Translate the language files of all mods in the background (only versions that implement it; today 1.12.2),
     * so that texts are Turkish the first time they are shown. Results go to the cache, so this costs requests once.
     */
    public boolean pretranslateLanguageFiles = true;

    /**
     * Use the shared translation pack: ready-made translations of mods' language files that are downloaded (static
     * files, nothing is uploaded) and used before a translation service is asked. Needs {@link #sharedPackUrl}.
     */
    public boolean sharedPacks = true;

    /**
     * Address of the pack site (https). Null means "not set": {@link SettingsStore} then fills in
     * {@link #DEFAULT_PACK_URL} when it reads the player's settings. Empty means no shared pack.
     */
    public String sharedPackUrl = null;

    /** The pack site of this release. */
    public static final String DEFAULT_PACK_URL = "https://autotranslation-packs.ergyusuf34.workers.dev";

    /**
     * Translation service: "auto" (default: the key decides - Gemini "AIza...", Groq "gsk_...", OpenRouter
     * "sk-or-..."; without a key the free, keyless Google machine translation), "google-free", "gemini", "groq",
     * "openrouter", "ollama", or "custom" (the endpoint and model below, e.g. OpenAI).
     */
    public String provider = "auto";

    /** When the free AI service's daily quota is used up, translate new texts with the keyless Google service meanwhile. */
    public boolean keylessFallback = true;

    public String endpoint = "https://api.openai.com/v1";
    public String model = "gpt-4o-mini";
    public int requestsPerMinute = 60;
    public int timeoutSeconds = 45;
    public int maxRetries = 2;
    public int queueSize = 512;
    public int cacheMaxEntries = 50000;
    public double temperature = 0.2;

    /** Extra evidence that a text is already in the target language (regular expression; see TextAnalyzer). */
    public String targetLanguageHint = "[çğıöşüÇĞİÖŞÜ]";

    /** Words that must never be translated (case-insensitive, single words). */
    public java.util.List<String> wordBlacklist = new java.util.ArrayList<String>();

    public boolean debug = false;
}
