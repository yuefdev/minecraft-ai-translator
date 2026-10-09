package me.langyue.autotranslation.engine;

import java.nio.file.Path;

/**
 * Everything the translation engine needs from the running game. Each Minecraft version / mod loader implements this
 * once; all other engine code is shared between versions.
 */
public interface Platform {
    /** Private data folder of the translator (cache, glossary, corrections, settings, secrets). */
    Path dataDir();

    /** Current game language in Minecraft's notation, for example {@code tr_tr}. */
    String languageCode();

    /** Runs the task on the game's main (client) thread. Must not block. */
    void runOnClientThread(Runnable task);

    boolean isModLoaded(String modId);
}
