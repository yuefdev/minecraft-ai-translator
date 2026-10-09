package me.langyue.autotranslation.tr;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.TranslatorHelper;
import me.langyue.autotranslation.config.Config;
import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.glossary.Glossary;
import me.langyue.autotranslation.core.glossary.GlossaryStore;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.lang.TextAnalyzer;
import me.langyue.autotranslation.core.provider.OpenAiCompatibleProvider;
import me.langyue.autotranslation.core.provider.OpenAiSettings;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.core.util.SecretStore;
import me.langyue.autotranslation.translate.TranslatorManager;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wires the Minecraft-independent translation engine ({@code core}) into the game: data folders, settings,
 * the client-thread callback executor and the refresh of open screens when translations arrive.
 */
public final class TranslationRuntime {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final List<Runnable> REFRESHERS = new CopyOnWriteArrayList<>();
    private static final Set<String> HOOKS_SEEN = ConcurrentHashMap.newKeySet();

    private static volatile TranslationService service;
    private static volatile OpenAiSettings openAi = OpenAiSettings.defaults();
    private static volatile Secret apiKey = Secret.empty();
    private static volatile GameLanguageIndex gameIndex = GameLanguageIndex.empty();
    private static final Glossary GLOSSARY = new Glossary();
    private static OpenAiCompatibleProvider provider;
    private static GlossaryStore glossaryStore;
    private static CorrectionStore corrections;
    private static TranslationCache cache;
    private static SecretStore secretStore;
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AutoTranslation-IO");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean refreshRequested = false;
    private static long lastRefreshMillis = 0L;

    private TranslationRuntime() {
    }

    /** Called once when the mod is constructed, after the configuration has been loaded. */
    public static synchronized void init() {
        if (service != null) return;
        CoreLog.setSink((level, message, error) -> {
            switch (level) {
                case DEBUG -> AutoTranslation.LOGGER.debug(message, error);
                case INFO -> AutoTranslation.LOGGER.info(message, error);
                case WARN -> AutoTranslation.LOGGER.warn(message, error);
                case ERROR -> AutoTranslation.LOGGER.error(message, error);
            }
        });
        CoreLog.setDebug(AutoTranslation.CONFIG.debug);
        Config.Turkish settings = AutoTranslation.CONFIG.turkish;

        secretStore = new SecretStore(AutoTranslation.ROOT.resolve("secrets").resolve("api-key.txt"));
        apiKey = secretStore.load();
        cache = new TranslationCache(AutoTranslation.ROOT.resolve("cache").resolve("translations.jsonl"),
                settings.cacheMaxEntries);
        cache.load();
        corrections = new CorrectionStore(AutoTranslation.ROOT.resolve("corrections.json"));
        corrections.load();
        glossaryStore = new GlossaryStore(AutoTranslation.ROOT.resolve("glossary.json"));
        GLOSSARY.replaceTerms(glossaryStore.load());
        openAi = buildOpenAiSettings();
        provider = new OpenAiCompatibleProvider(() -> openAi);

        service = TranslationService.builder()
                .settings(buildServiceSettings())
                .provider(() -> provider)
                .cache(cache)
                .corrections(corrections)
                .glossary(GLOSSARY)
                .gameLanguage(() -> gameIndex)
                .analyzer(buildAnalyzer())
                .callbackExecutor(TranslationRuntime::runOnClientThread)
                .workers(2)
                .build();

        ClientTickEvent.CLIENT_POST.register(TranslationRuntime::tick);
        if (Boolean.getBoolean("autotranslation.smoketest")) {
            me.langyue.autotranslation.smoke.SmokeTest.register();
        }
        AutoTranslation.LOGGER.info("Translation pipeline ready (cache: {} entries, provider configured: {})",
                cache.size(), provider.isConfigured());
    }

    // ----------------------------------------------------------------------------------------- accessors

    public static TranslationService service() {
        return service;
    }

    public static OpenAiCompatibleProvider provider() {
        return provider;
    }

    public static Glossary glossary() {
        return GLOSSARY;
    }

    public static CorrectionStore corrections() {
        return corrections;
    }

    public static SecretStore secrets() {
        return secretStore;
    }

    public static Secret apiKey() {
        return apiKey;
    }

    public static boolean providerConfigured() {
        return provider != null && provider.isConfigured();
    }

    /** Whether new-style (OpenAI compatible) translation is selected instead of the legacy Google engine. */
    public static boolean usesPipeline() {
        return service != null && TranslatorManager.OPENAI_TRANSLATOR.equalsIgnoreCase(AutoTranslation.CONFIG.translator);
    }

    /** Pipeline selected, switched on and the game is not running in the source language. */
    public static boolean active() {
        return service != null && AutoTranslation.CONFIG.turkish.enabled && !currentLanguageIsDefault();
    }

    public static boolean questsEnabled() {
        return active() && AutoTranslation.CONFIG.turkish.translateQuests;
    }

    public static boolean booksEnabled() {
        return active() && AutoTranslation.CONFIG.turkish.translateBooks;
    }

    public static boolean genericEnabled() {
        return active() && AutoTranslation.CONFIG.turkish.translateGeneric;
    }

    public static boolean showOriginal() {
        return AutoTranslation.CONFIG.turkish.showOriginal;
    }

    private static boolean currentLanguageIsDefault() {
        return AutoTranslation.getLanguage().equals(Language.DEFAULT);
    }

    // ---------------------------------------------------------------------------------- change tracking

    /** Changes whenever any translation arrives or a setting changes; display caches are keyed by it. */
    public static long generation() {
        return GENERATION.get();
    }

    /** Called (on the client thread) by the service whenever a request finished. */
    public static void onTranslationArrived() {
        GENERATION.incrementAndGet();
        refreshRequested = true;
    }

    public static void registerRefresher(Runnable refresher) {
        REFRESHERS.add(refresher);
    }

    /** Records that an integration hook has actually run (shown on the status screen). */
    public static void markHook(String name) {
        if (!HOOKS_SEEN.contains(name)) HOOKS_SEEN.add(name);
    }

    public static Set<String> hooksSeen() {
        return new HashSet<>(HOOKS_SEEN);
    }

    private static void tick(Minecraft minecraft) {
        if (!refreshRequested) return;
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < 400) return; // batch bursts of arrivals into one screen refresh
        refreshRequested = false;
        lastRefreshMillis = now;
        for (Runnable refresher : REFRESHERS) {
            try {
                refresher.run();
            } catch (Throwable t) {
                AutoTranslation.LOGGER.warn("Screen refresh failed: {}", t.toString());
            }
        }
    }

    private static void runOnClientThread(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            task.run();
        } else {
            minecraft.execute(task);
        }
    }

    // ------------------------------------------------------------------------------------ configuration

    /** Re-reads the configuration; called after the settings screen saved and when the language changes. */
    public static void applyConfig() {
        if (service == null) return;
        CoreLog.setDebug(AutoTranslation.CONFIG.debug);
        openAi = buildOpenAiSettings();
        service.updateSettings(buildServiceSettings());
        service.setAnalyzer(buildAnalyzer());
        service.clearFailures();
        GENERATION.incrementAndGet();
        refreshRequested = true;
    }

    /** Disk writes started from the UI run on a background thread so that the render thread never waits for them. */
    public static void io(Runnable task) {
        IO.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                AutoTranslation.LOGGER.warn("Background file operation failed: {}", e.toString());
            }
        });
    }

    public static void setApiKey(Secret key) throws IOException {
        apiKey = key; // takes effect immediately; the file is written in the background
        io(() -> {
            try {
                secretStore.save(key);
            } catch (IOException e) {
                AutoTranslation.LOGGER.warn("Could not store the API key: {}", e.getClass().getSimpleName());
            }
        });
        applyConfig();
    }

    public static void saveGlossary() {
        Map<String, String> snapshot = new java.util.LinkedHashMap<>(GLOSSARY.terms());
        io(() -> {
            try {
                glossaryStore.save(snapshot);
            } catch (IOException e) {
                AutoTranslation.LOGGER.warn("Could not save the glossary: {}", e.toString());
            }
        });
        service.invalidateMemo();
        GENERATION.incrementAndGet();
        refreshRequested = true;
    }

    public static void saveCorrection(String source, String translation) {
        corrections.putInMemory(source, translation, null);
        io(() -> {
            try {
                corrections.save();
            } catch (IOException e) {
                AutoTranslation.LOGGER.warn("Could not save the correction: {}", e.toString());
            }
        });
    }

    public static void removeCorrection(String source) {
        corrections.removeFromMemory(source, null);
        io(() -> {
            try {
                corrections.save();
            } catch (IOException e) {
                AutoTranslation.LOGGER.warn("Could not remove the correction: {}", e.toString());
            }
        });
    }

    /** Called when the language files were (re)loaded: the game's own Turkish names become glossary hints. */
    public static void onLanguageLoaded(Map<String, String> english, Map<String, String> target) {
        if (service == null) return;
        try {
            gameIndex = GameLanguageIndex.build(english, target);
            GLOSSARY.replaceHints(gameIndex.asMap());
        } catch (RuntimeException e) {
            AutoTranslation.LOGGER.warn("Could not index the game's language files: {}", e.toString());
        }
        applyConfig();
    }

    public static GameLanguageIndex gameIndex() {
        return gameIndex;
    }

    private static OpenAiSettings buildOpenAiSettings() {
        Config.Turkish t = AutoTranslation.CONFIG.turkish;
        int timeout = Math.max(5, Math.min(300, t.timeoutSeconds)) * 1000;
        return new OpenAiSettings(t.endpoint, t.model, apiKey, 10_000, timeout, t.temperature, 4096, true);
    }

    private static TranslationService.Settings buildServiceSettings() {
        Config.Turkish t = AutoTranslation.CONFIG.turkish;
        LanguageProfile target = LanguageProfile.of(AutoTranslation.getLanguage());
        return new TranslationService.Settings(
                t.enabled,
                target,
                Math.max(16, Math.min(5000, t.queueSize)),
                Math.max(1, Math.min(6000, t.requestsPerMinute)),
                Math.max(5, Math.min(300, t.timeoutSeconds)) * 1000,
                Math.max(0, Math.min(5, t.maxRetries)),
                1_500,
                3_000,
                24,
                10 * 60_000L,
                5,
                60_000L,
                30);
    }

    private static TextAnalyzer buildAnalyzer() {
        Set<String> excluded = new HashSet<>();
        for (String word : TranslatorHelper.noNeedForTranslation) {
            String lower = word.toLowerCase(java.util.Locale.ROOT).trim();
            if (!lower.isEmpty() && lower.indexOf(' ') < 0) excluded.add(lower);
        }
        return new TextAnalyzer(AutoTranslation.CONFIG.yourLanguageFeature, excluded);
    }

    /** True if a mod with this id is loaded (safe to call before the mod list is complete). */
    public static boolean isModLoaded(String id) {
        try {
            return Platform.isModLoaded(id);
        } catch (Throwable t) {
            return false;
        }
    }
}
