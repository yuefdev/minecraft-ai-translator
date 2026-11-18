package me.langyue.autotranslation.mc.generic;

import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.engine.Engine;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Language-file text of any mod: whatever a key resolves to is looked at once per state of the translator; if it is
 * English prose it is translated in the background (the English text is shown until the answer arrives). Keys already
 * localized by the game, mods, or resource packs are skipped immediately and indexed for glossary hints.
 */
public final class GenericLanguage {
    private static final class Entry {
        final String value;
        final long generation;
        final String translated;

        Entry(String value, long generation, String translated) {
            this.value = value;
            this.generation = generation;
            this.translated = translated;
        }
    }

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<String, Entry>();
    private static volatile Set<String> localizedKeys = Collections.emptySet();
    private static volatile Object lastLanguageInstance;
    private static volatile String lastLanguageCode;
    private static volatile boolean indexing;

    private GenericLanguage() {
    }

    /** @return the translation, or null when the text must stay as it is */
    public static String translate(String key, String value) {
        Engine engine = Engine.get();
        if (engine == null || value == null || value.length() < 2 || !engine.displayGenericEnabled()) return null;
        if (localizedKeys.contains(key)) return null;
        long generation = engine.generation();
        Entry entry = CACHE.get(key);
        if (entry != null && entry.generation == generation && entry.value.equals(value)) return entry.translated;
        String translated = engine.translateLegacy(key, value, null);
        if (CACHE.size() > 60000) CACHE.clear();
        CACHE.put(key, new Entry(value, generation, translated));
        return translated;
    }

    /**
     * Indexes vanilla/mod language files once per language reload on a background thread so keys already translated
     * into the active target language are skipped and entity/item names feed {@link GameLanguageIndex}.
     */
    public static void syncIfNeeded(final Engine engine) {
        if (engine == null || !engine.settings().enabled || Boolean.getBoolean("autotranslation.smoketest")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.getOverlay() != null) return;
        final Language currentLang = Language.getInstance();
        final String rawCode = minecraft.options.languageCode;
        if (currentLang == null || rawCode == null) return;
        final String lang = rawCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("en_us".equals(lang)) {
            if (!lang.equals(lastLanguageCode) || currentLang != lastLanguageInstance) {
                localizedKeys = Collections.emptySet();
                lastLanguageInstance = currentLang;
                lastLanguageCode = lang;
            }
            return;
        }
        if (indexing || (currentLang == lastLanguageInstance && lang.equals(lastLanguageCode))) return;
        final ResourceManager manager = minecraft.getResourceManager();
        if (manager == null) return;
        indexing = true;
        lastLanguageInstance = currentLang;
        lastLanguageCode = lang;
        engine.io(new Runnable() {
            @Override
            public void run() {
                try {
                    indexLanguages(engine, manager, lang);
                } catch (Throwable t) {
                    CoreLog.debug("Background language indexing skipped: " + t);
                    lastLanguageInstance = null;
                } finally {
                    indexing = false;
                }
            }
        });
    }

    private static void indexLanguages(Engine engine, ResourceManager manager, String lang) {
        Map<String, String> englishAll = new LinkedHashMap<String, String>();
        Map<String, String> targetAll = new LinkedHashMap<String, String>();
        Set<String> minecraftTargetKeys = new HashSet<String>();
        List<String> namespaces = new ArrayList<String>(manager.getNamespaces());
        for (String ns : namespaces) {
            readLang(manager, ns, "en_us", englishAll, null);
            readLang(manager, ns, lang, targetAll, "minecraft".equals(ns) ? minecraftTargetKeys : null);
        }
        Set<String> alreadyLocalized = new HashSet<String>();
        Map<String, String> englishNames = new LinkedHashMap<String, String>();
        Map<String, String> targetNames = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : targetAll.entrySet()) {
            String key = entry.getKey();
            String targetVal = entry.getValue();
            String enVal = englishAll.get(key);
            if (minecraftTargetKeys.contains(key) || (targetVal != null && !targetVal.equals(enVal))) {
                alreadyLocalized.add(key);
                if (enVal != null && GameLanguageIndex.isNameKey(key)) {
                    englishNames.put(key, enVal);
                    targetNames.put(key, targetVal);
                }
            }
        }
        if (lang.equals(lastLanguageCode) && Engine.get() == engine) {
            localizedKeys = Collections.unmodifiableSet(alreadyLocalized);
            CACHE.clear();
            engine.onLanguageLoaded(englishNames, targetNames);
        }
    }

    private static void readLang(ResourceManager manager, String ns, String lang, final Map<String, String> sink,
                                 final Set<String> keyRecorder) {
        ResourceLocation id;
        try {
            id = ResourceLocation.tryBuild(ns, "lang/" + lang + ".json");
        } catch (RuntimeException e) {
            return;
        }
        if (id == null) return;
        List<Resource> resources = manager.getResourceStack(id);
        if (resources == null || resources.isEmpty()) return;
        BiConsumer<String, String> consumer = new BiConsumer<String, String>() {
            @Override
            public void accept(String k, String v) {
                sink.put(k, v);
                if (keyRecorder != null) keyRecorder.add(k);
            }
        };
        for (Resource resource : resources) {
            try (InputStream stream = resource.open()) {
                Language.loadFromJson(stream, consumer);
            } catch (IOException | RuntimeException ignored) {
                // Skip unreadable or malformed language files cleanly.
            }
        }
    }
}
