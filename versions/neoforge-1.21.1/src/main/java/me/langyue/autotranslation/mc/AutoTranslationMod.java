package me.langyue.autotranslation.mc;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Entry point. The translator is a pure client side mod: nothing is started on a dedicated server. */
@Mod(value = AutoTranslationMod.MOD_ID, dist = Dist.CLIENT)
public final class AutoTranslationMod {
    public static final String MOD_ID = "autotranslation";

    public AutoTranslationMod(IEventBus modBus, ModContainer container) {
        ClientInit.init(FMLPaths.GAMEDIR.get(), modId -> ModList.get().isLoaded(modId),
                task -> NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> task.run()));
    }
}
