package me.langyue.autotranslation.engine;

import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.StoredTranslation;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.glossary.Glossary;
import me.langyue.autotranslation.core.glossary.GlossaryStore;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.lang.TextAnalyzer;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.provider.OpenAiCompatibleProvider;
import me.langyue.autotranslation.core.provider.OpenAiSettings;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.Segment;
import me.langyue.autotranslation.core.service.Types.State;
import me.langyue.autotranslation.core.service.Types.TextRequest;
import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.core.util.SecretStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The version independent heart of the mod: owns the translation service and its stores, exposes the small API that
 * the per-version hooks call (all of it non-blocking), and keeps displays up to date when translations arrive.
 *
 * <p>Every method that hooks may call from rendering code is memoised per {@link #generation()}, so a text is never
 * looked up twice for the same state of the translator. A {@code null} result always means "show the original".
 */
public final class Engine {
    private static volatile Engine instance;

    /** The running engine, or null before it was created (hooks must tolerate that). */
    public static Engine get() {
        return instance;
    }

    public static final int MEMO_LIMIT = 20000;
    private static final String NONE = "";

    private final Platform platform;
    private final EngineSettings settings;
    private final AtomicLong generation = new AtomicLong();
    private final List<Runnable> refreshers = new CopyOnWriteArrayList<Runnable>();
    private final Set<String> hooksSeen = ConcurrentHashMap.newKeySet();
    private final Map<String, String> textMemo = new ConcurrentHashMap<String, String>();
    private final Map<String, List<String>> linesMemo = new ConcurrentHashMap<String, List<String>>();
    private volatile long memoGeneration = -1;

    private final Glossary glossary = new Glossary();
    private final GlossaryStore glossaryStore;
    private final CorrectionStore corrections;
    private final TranslationCache cache;
    private final SecretStore secretStore;
    private final OpenAiCompatibleProvider provider;
    private final me.langyue.autotranslation.core.provider.GoogleFreeProvider googleFree;
    private volatile me.langyue.autotranslation.core.provider.ProviderSelection selection;
    private final TranslationService service;
    private final ExecutorService io;
    private volatile OpenAiSettings openAi = OpenAiSettings.defaults();
    private volatile Secret apiKey = Secret.empty();
    private volatile GameLanguageIndex gameIndex = GameLanguageIndex.empty();
    private volatile boolean refreshRequested = false;
    private long lastRefreshMillis = 0L;
    private String lastLanguage;

    /** Creates the engine and makes it the {@linkplain #get() current} one. */
    public static Engine start(Platform platform, EngineSettings settings) {
        Engine engine = new Engine(platform, settings);
        instance = engine;
        return engine;
    }

    private Engine(Platform platform, EngineSettings settings) {
        this.platform = platform;
        this.settings = settings;
        CoreLog.setDebug(settings.debug);
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AutoTranslation-IO");
                t.setDaemon(true);
                return t;
            }
        });
        secretStore = new SecretStore(platform.dataDir().resolve("secrets").resolve("api-key.txt"));
        apiKey = secretStore.load();
        cache = new TranslationCache(platform.dataDir().resolve("cache").resolve("translations.jsonl"), settings.cacheMaxEntries);
        cache.load();
        corrections = new CorrectionStore(platform.dataDir().resolve("corrections.json"));
        corrections.load();
        glossaryStore = new GlossaryStore(platform.dataDir().resolve("glossary.json"));
        glossary.replaceTerms(glossaryStore.load());
        selection = select();
        openAi = buildOpenAiSettings();
        provider = new OpenAiCompatibleProvider(new java.util.function.Supplier<OpenAiSettings>() {
            @Override
            public OpenAiSettings get() {
                return openAi;
            }
        });
        googleFree = new me.langyue.autotranslation.core.provider.GoogleFreeProvider(new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return selection.baseUrl();
            }
        }, new java.util.function.IntSupplier() {
            @Override
            public int getAsInt() {
                return Math.max(5, Math.min(300, Engine.this.settings.timeoutSeconds)) * 1000;
            }
        });
        service = TranslationService.builder()
                .settings(buildServiceSettings())
                .provider(new java.util.function.Supplier<me.langyue.autotranslation.core.provider.TranslationProvider>() {
                    @Override
                    public me.langyue.autotranslation.core.provider.TranslationProvider get() {
                        return activeProvider();
                    }
                })
                .cache(cache)
                .corrections(corrections)
                .glossary(glossary)
                .gameLanguage(new java.util.function.Supplier<GameLanguageIndex>() {
                    @Override
                    public GameLanguageIndex get() {
                        return gameIndex;
                    }
                })
                .analyzer(buildAnalyzer())
                .callbackExecutor(new java.util.concurrent.Executor() {
                    @Override
                    public void execute(Runnable command) {
                        Engine.this.platform.runOnClientThread(command);
                    }
                })
                .workers(2)
                .coalesceShortTexts(true)
                .build();
        CoreLog.info("Translation engine ready (cache: " + cache.size() + " entries, provider configured: "
                + provider.isConfigured() + ")");
    }

    // ----------------------------------------------------------------------------------------- accessors

    public TranslationService service() {
        return service;
    }

    public Glossary glossary() {
        return glossary;
    }

    public CorrectionStore corrections() {
        return corrections;
    }

    public SecretStore secrets() {
        return secretStore;
    }

    public EngineSettings settings() {
        return settings;
    }

    public Secret apiKey() {
        return apiKey;
    }

    public OpenAiCompatibleProvider provider() {
        return provider;
    }

    /** The service that translations are sent to now (see {@link #selection()}). */
    public me.langyue.autotranslation.core.provider.TranslationProvider activeProvider() {
        return selection.usesKeylessMachineTranslation() ? googleFree : provider;
    }

    /** Which service, address and model are in use, after "auto" and the presets were worked out. */
    public me.langyue.autotranslation.core.provider.ProviderSelection selection() {
        return selection;
    }

    private me.langyue.autotranslation.core.provider.ProviderSelection select() {
        return me.langyue.autotranslation.core.provider.ProviderSelection.resolve(settings.provider, apiKey,
                settings.endpoint, settings.model, Math.max(1, Math.min(6000, settings.requestsPerMinute)));
    }

    public boolean providerConfigured() {
        return activeProvider().isConfigured();
    }

    public GameLanguageIndex gameIndex() {
        return gameIndex;
    }

    public boolean isModLoaded(String modId) {
        try {
            return platform.isModLoaded(modId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean currentLanguageIsDefault() {
        return "en_us".equalsIgnoreCase(platform.languageCode());
    }

    /** Switched on in the settings and the game is not running in the source language. */
    public boolean active() {
        return settings.enabled && !currentLanguageIsDefault();
    }

    public boolean questsEnabled() {
        return active() && settings.translateQuests;
    }

    public boolean booksEnabled() {
        return active() && settings.translateBooks;
    }

    public boolean genericEnabled() {
        return active() && settings.translateGeneric;
    }

    public boolean showOriginal() {
        return settings.showOriginal;
    }

    // ---------------------------------------------------------------------------------- change tracking

    /** Changes whenever any translation arrives or a setting changes; display caches are keyed by it. */
    public long generation() {
        return generation.get();
    }

    /** Called (on the client thread) whenever a request finished. */
    public void onTranslationArrived() {
        generation.incrementAndGet();
        refreshRequested = true;
    }

    public void registerRefresher(Runnable refresher) {
        refreshers.add(refresher);
    }

    /** Records that an integration hook has actually run (shown on the status screen). */
    public void markHook(String name) {
        if (!hooksSeen.contains(name)) hooksSeen.add(name);
    }

    public Set<String> hooksSeen() {
        return new HashSet<String>(hooksSeen);
    }

    /** Call once per client tick: refreshes open screens (at most every 400 ms) after translations arrived. */
    public void tick() {
        String language = platform.languageCode();
        if (language != null && !language.equals(lastLanguage)) {
            boolean changed = lastLanguage != null;
            lastLanguage = language;
            if (changed) applyConfig(); // new target language: lookups are keyed by it, displays must be rebuilt
        }
        if (!refreshRequested) return;
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < 400) return;
        refreshRequested = false;
        lastRefreshMillis = now;
        for (Runnable refresher : refreshers) {
            try {
                refresher.run();
            } catch (Throwable t) {
                CoreLog.warn("Screen refresh failed: " + t);
            }
        }
    }

    // ------------------------------------------------------------------------------------ configuration

    /** Re-reads the settings; call after the user changed them and when the language changed. */
    public void applyConfig() {
        CoreLog.setDebug(settings.debug);
        selection = select();
        openAi = buildOpenAiSettings();
        service.updateSettings(buildServiceSettings());
        service.setAnalyzer(buildAnalyzer());
        service.clearFailures();
        generation.incrementAndGet();
        refreshRequested = true;
    }

    /** Disk writes started from the UI run on a background thread. */
    public void io(final Runnable task) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    CoreLog.warn("Background file operation failed: " + e);
                }
            }
        });
    }

    public void setApiKey(final Secret key) {
        apiKey = key;
        io(new Runnable() {
            @Override
            public void run() {
                try {
                    secretStore.save(key);
                } catch (IOException e) {
                    CoreLog.warn("Could not store the API key: " + e.getClass().getSimpleName());
                }
            }
        });
        applyConfig();
    }

    public void saveGlossary() {
        final Map<String, String> snapshot = new LinkedHashMap<String, String>(glossary.terms());
        io(new Runnable() {
            @Override
            public void run() {
                try {
                    glossaryStore.save(snapshot);
                } catch (IOException e) {
                    CoreLog.warn("Could not save the glossary: " + e);
                }
            }
        });
        service.invalidateMemo();
        generation.incrementAndGet();
        refreshRequested = true;
    }

    public void saveCorrection(String source, String translation) {
        corrections.putInMemory(source, translation, null);
        saveCorrectionsAsync();
    }

    public void removeCorrection(String source) {
        corrections.removeFromMemory(source, null);
        saveCorrectionsAsync();
    }

    private void saveCorrectionsAsync() {
        io(new Runnable() {
            @Override
            public void run() {
                try {
                    corrections.save();
                } catch (IOException e) {
                    CoreLog.warn("Could not save the corrections: " + e);
                }
            }
        });
        service.invalidateMemo();
        onTranslationArrived();
    }

    /** Called when the game's language files were (re)loaded: its own Turkish names become glossary hints. */
    public void onLanguageLoaded(Map<String, String> english, Map<String, String> target) {
        try {
            gameIndex = GameLanguageIndex.build(english, target);
            glossary.replaceHints(gameIndex.asMap());
        } catch (RuntimeException e) {
            CoreLog.warn("Could not index the game's language files: " + e);
        }
        applyConfig();
    }

    private OpenAiSettings buildOpenAiSettings() {
        int timeout = Math.max(5, Math.min(300, settings.timeoutSeconds)) * 1000;
        return new OpenAiSettings(selection.baseUrl(), selection.model(), apiKey, 10000, timeout, selection.sendTemperature() ? settings.temperature : Double.NaN, 8192, true,
                selection.extraParameters(), selection.fallbackModels());
    }

    private TranslationService.Settings buildServiceSettings() {
        return new TranslationService.Settings(
                settings.enabled,
                LanguageProfile.of(platform.languageCode()),
                Math.max(16, Math.min(5000, settings.queueSize)),
                selection.requestsPerMinute(),
                Math.max(5, Math.min(300, settings.timeoutSeconds)) * 1000,
                Math.max(0, Math.min(5, settings.maxRetries)),
                1500,
                selection.maxBatchChars(),
                selection.maxSegmentsPerBatch(),
                10 * 60000L,
                5,
                60000L,
                30);
    }

    private TextAnalyzer buildAnalyzer() {
        Set<String> excluded = new HashSet<String>();
        for (String word : DEFAULT_UNTRANSLATED) excluded.add(word.toLowerCase(Locale.ROOT));
        if (settings.wordBlacklist != null) {
            for (String word : settings.wordBlacklist) {
                String lower = word.toLowerCase(Locale.ROOT).trim();
                if (!lower.isEmpty() && lower.indexOf(' ') < 0) excluded.add(lower);
            }
        }
        return new TextAnalyzer(settings.targetLanguageHint, excluded);
    }

    /** Words that are the same in every language (key names, platform names, ...). */
    private static final String[] DEFAULT_UNTRANSLATED = {
            "esc", "tab", "caps", "ins", "del", "home", "end", "ctrl", "shift", "alt", "java", "minecraft", "mc",
            "modrinth", "curseforge", "fabric", "forge", "neoforge", "discord", "patreon", "youtube", "twitch",
            "twitter", "paypal", "reddit", "fps", "tps", "ping", "max", "min", "avg", "id", "eg"
    };

    // ------------------------------------------------------------------------------------------ bridge

    private void syncMemo() {
        long current = generation.get();
        if (current != memoGeneration) {
            textMemo.clear();
            linesMemo.clear();
            memoGeneration = current;
        }
        if (textMemo.size() > MEMO_LIMIT) textMemo.clear();
        if (linesMemo.size() > MEMO_LIMIT) linesMemo.clear();
    }

    private Consumer<Result> arrival() {
        return new Consumer<Result>() {
            @Override
            public void accept(Result result) {
                onTranslationArrived();
            }
        };
    }

    /** @return the translation, or null if the original must be shown (not ready, not needed, disabled, failed) */
    public String translate(String text, Content content, MaskProfile profile) {
        if (text == null || text.isEmpty() || !active()) return null;
        syncMemo();
        String memoKey = content.contentId() + '\u0001' + profile.ordinal() + '\u0001' + text;
        String memo = textMemo.get(memoKey);
        if (memo != null) return memo.isEmpty() ? null : memo;

        Result result = service.lookup(new TextRequest(Compat.listOf(Segment.plain(text)), content, profile, memoKey), arrival());
        String translated = null;
        if (result.state() == State.READY) {
            StoredTranslation t = result.segments().get(0).translation();
            if (t != null && !t.text().equals(text)) translated = t.text();
        }
        textMemo.put(memoKey, translated == null ? NONE : translated);
        return translated;
    }

    /**
     * Translates related lines together so the provider sees the whole passage. The returned list has the same size
     * and order; lines that need no translation are returned unchanged. Null if nothing is available yet.
     */
    public List<String> translateLines(List<String> lines, Content content, MaskProfile profile) {
        if (lines == null || lines.isEmpty() || !active()) return null;
        syncMemo();
        StringBuilder key = new StringBuilder(content.contentId()).append('\u0001').append(profile.ordinal());
        for (String line : lines) key.append('\u0001').append(line);
        String memoKey = key.toString();
        List<String> memo = linesMemo.get(memoKey);
        if (memo != null) return memo.isEmpty() ? null : memo;

        List<Segment> segments = new ArrayList<Segment>(lines.size());
        for (String line : lines) segments.add(Segment.plain(line));
        Result result = service.lookup(new TextRequest(segments, content, profile, memoKey), arrival());
        List<String> translated = null;
        if (result.state() == State.READY) {
            boolean any = false;
            List<String> out = new ArrayList<String>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                StoredTranslation t = result.segments().get(i).translation();
                if (t != null && !t.text().equals(lines.get(i))) {
                    out.add(t.text());
                    any = true;
                } else {
                    out.add(lines.get(i));
                }
            }
            if (any) translated = Compat.listCopyOf(out);
        }
        linesMemo.put(memoKey, translated == null ? new ArrayList<String>() : translated);
        return translated;
    }

    /**
     * Entry point for language-file / interface text ({@code key} is the translation key or null). The original is
     * escaped when appended because the result is later used as a printf format string.
     */
    public String translateLegacy(final String key, final String english, final Consumer<String> callback) {
        if (english == null || !genericEnabled()) return null;
        final boolean isKey = key != null && !key.equals(english);
        final String contentId = isKey ? "lang:" + key : "ui";
        final Content content = new Content("game", isKey ? "language file" : "interface text", contentId, "");
        final boolean append = showOriginal();
        String translated = translate(english, content, MaskProfile.PLAIN);
        if (translated != null) {
            String finalText = TextSupport.appendOriginal(translated, english, append);
            if (callback != null) callback.accept(finalText);
            return finalText;
        }
        if (callback != null) {
            service.lookup(new TextRequest(Compat.listOf(Segment.plain(english)), content, MaskProfile.PLAIN,
                            "cb:" + contentId + '\u0001' + english),
                    new Consumer<Result>() {
                        @Override
                        public void accept(Result r) {
                            StoredTranslation t = r.segments().get(0).translation();
                            if (t != null && !t.text().equals(english)) {
                                callback.accept(TextSupport.appendOriginal(t.text(), english, append));
                            }
                        }
                    });
        }
        return null;
    }

    /** Cheap hot-path check: could this text be translated at all? */
    public boolean needsTranslation(String text) {
        return genericEnabled() && service.needsTranslation(text, MaskProfile.PLAIN);
    }

    public void shutdown() {
        service.close();
        io.shutdown();
        if (instance == this) instance = null;
    }

    /** Result type re-exported for hooks that only import the engine package. */
    public static boolean isReady(Types.Result result) {
        return result != null && result.state() == State.READY;
    }
}
