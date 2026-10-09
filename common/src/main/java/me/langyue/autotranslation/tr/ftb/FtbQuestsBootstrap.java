package me.langyue.autotranslation.tr.ftb;

import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.tr.TranslationRuntime;

/** Loaded only when FTB Quests is installed. */
public final class FtbQuestsBootstrap {
    private FtbQuestsBootstrap() {
    }

    public static void init() {
        TranslationRuntime.registerRefresher(FtbQuestsSupport::refreshOpenScreen);
        AutoTranslation.LOGGER.info("FTB Quests detected: quest text will be translated at display time");
    }
}
