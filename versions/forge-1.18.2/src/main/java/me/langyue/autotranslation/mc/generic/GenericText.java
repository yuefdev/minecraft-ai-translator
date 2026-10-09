package me.langyue.autotranslation.mc.generic;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.engine.Engine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Hard-coded text that is turned into pixels: asked once per state of the translator for every distinct string, so
 * drawing it every frame costs one hash lookup. English prose is translated in the background and shown in English
 * until the answer arrives.
 */
public final class GenericText {
    private static final Content CONTENT = new Content("game", "interface text", "ui", "");
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<String, String>();
    private static volatile long cacheGeneration = -1L;
    private static final String NONE = "";

    private GenericText() {
    }

    /** @return the text to draw (the argument itself when it must stay as it is) */
    public static String translate(String text) {
        if (text == null || text.length() < 2) return text;
        Engine engine = Engine.get();
        if (engine == null || !engine.genericEnabled() || !GenericGate.screenAllowed()) return text;
        long generation = engine.generation();
        if (generation != cacheGeneration || CACHE.size() > 60000) {
            CACHE.clear();
            cacheGeneration = generation;
        }
        String cached = CACHE.get(text);
        if (cached == null) {
            String translated = engine.translate(text, CONTENT, MaskProfile.PLAIN);
            cached = translated == null ? NONE : translated;
            CACHE.put(text, cached);
        }
        return cached.isEmpty() ? text : cached;
    }
}
