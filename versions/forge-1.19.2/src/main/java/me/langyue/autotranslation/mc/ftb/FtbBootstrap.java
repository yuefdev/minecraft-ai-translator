package me.langyue.autotranslation.mc.ftb;

import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.engine.Engine;

/** Loaded only when FTB Quests is installed. */
public final class FtbBootstrap {
    private FtbBootstrap() {
    }

    public static void init() {
        Engine.get().registerRefresher(FtbQuestsSupport::refreshOpenScreen);
        CoreLog.info("FTB Quests detected: quest text will be translated at display time");
    }
}
