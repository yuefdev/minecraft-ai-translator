package me.langyue.autotranslation.forge;

import dev.architectury.platform.forge.EventBuses;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.ScreenTranslationHelper;
import me.langyue.autotranslation.gui.TranslatorSettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AutoTranslation.MOD_ID)
public class AutoTranslationForge {
    public AutoTranslationForge() {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(AutoTranslation.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        AutoTranslation.init();
        ScreenTranslationHelper.addScreenBlacklist(ModListScreen.class);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, screen) -> new TranslatorSettingsScreen(screen)
                )
        );
        MinecraftForge.EVENT_BUS.register(ServerStoppingEventHandler.class);
    }

    public static class ServerStoppingEventHandler {
        @SubscribeEvent
        public static void serverStopping(ServerStoppingEvent event) {
            AutoTranslation.stop();
        }
    }
}