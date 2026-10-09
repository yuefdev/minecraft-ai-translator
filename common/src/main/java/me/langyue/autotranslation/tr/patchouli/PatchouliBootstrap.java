package me.langyue.autotranslation.tr.patchouli;

import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.tr.TranslationRuntime;

/** Loaded only when Patchouli is installed. */
public final class PatchouliBootstrap {
    private PatchouliBootstrap() {
    }

    public static void init() {
        TranslationRuntime.registerRefresher(PatchouliSupport::refreshOpenScreen);
        AutoTranslation.LOGGER.info("Patchouli detected: book text will be translated before layout");
    }
}
