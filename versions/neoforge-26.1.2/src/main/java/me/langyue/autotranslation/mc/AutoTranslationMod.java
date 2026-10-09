package me.langyue.autotranslation.mc;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Entry point. The translator is a pure client side mod: nothing is started on a dedicated server. */
@Mod(value = AutoTranslationMod.MOD_ID, dist = Dist.CLIENT)
public final class AutoTranslationMod {
    public static final String MOD_ID = "autotranslation";

    public AutoTranslationMod(IEventBus modBus, ModContainer container) {
        ClientInit.init();
    }
}
