package me.langyue.autotranslation.mc.patchouli;

import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.engine.Engine;

/** Loaded only when Patchouli is installed. */
public final class PatchouliBootstrap {
    private PatchouliBootstrap() {
    }

    public static void init() {
        Engine.get().registerRefresher(PatchouliSupport::refreshOpenScreen);
        CoreLog.info("Patchouli detected: book text will be translated before layout");
    }
}
