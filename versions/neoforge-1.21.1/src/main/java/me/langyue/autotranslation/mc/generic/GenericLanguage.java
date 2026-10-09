package me.langyue.autotranslation.mc.generic;

import me.langyue.autotranslation.engine.Engine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Language-file text of any mod: whatever a key resolves to is looked at once per state of the translator; if it is
 * English prose it is translated in the background (the English text is shown until the answer arrives). Text that is
 * already Turkish, keys without a value and non-prose are left alone by the engine's language analysis.
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

    private GenericLanguage() {
    }

    /** @return the translation, or null when the text must stay as it is */
    public static String translate(String key, String value) {
        Engine engine = Engine.get();
        if (engine == null || value == null || value.length() < 2 || !engine.genericEnabled()) return null;
        long generation = engine.generation();
        Entry entry = CACHE.get(key);
        if (entry != null && entry.generation == generation && entry.value.equals(value)) return entry.translated;
        String translated = engine.translateLegacy(key, value, null);
        if (CACHE.size() > 60000) CACHE.clear();
        CACHE.put(key, new Entry(value, generation, translated));
        return translated;
    }
}
