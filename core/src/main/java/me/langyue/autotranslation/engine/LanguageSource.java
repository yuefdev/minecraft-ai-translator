package me.langyue.autotranslation.engine;

import java.io.IOException;
import java.util.Map;

/** The texts of the game's language files, as seen by the pre-translation. Implemented once per Minecraft version. */
public interface LanguageSource {
    /**
     * Every language key of every mod that has a text only in the source language (English) and none in {@code
     * targetLanguage}, with that English text exactly as the game's own lookup returns it. The order is the order in
     * which the texts are translated: names and other things a player sees first, the rest after them.
     *
     * <p>Called on a background thread; may take a moment. Throw {@link IOException} if the files cannot be read yet
     * (the pre-translation tries again later).
     */
    Map<String, String> untranslated(String targetLanguage) throws IOException;
}
