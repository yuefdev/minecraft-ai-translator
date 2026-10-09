package me.langyue.autotranslation.forge;

import dev.architectury.platform.forge.EventBuses;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.ScreenTranslationHelper;
import me.langyue.autotranslation.gui.TranslatorSettingsScreen;
import me.langyue.autotranslation.tr.Integrations;
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
        // Optional integrations. The lambdas only touch FTB Quests / Patchouli classes when they actually run,
        // which happens only if the mod is installed.
        Integrations.register("ftbquests", () -> me.langyue.autotranslation.forge.tr.ftb.FtbQuestsBootstrap.init());
        Integrations.register("patchouli", () -> me.langyue.autotranslation.forge.tr.patchouli.PatchouliBootstrap.init());
        Integrations.registerSmokeCheck("ftbquests", "dev.ftb.mods.ftbquests.quest.QuestObjectBase",
                () -> me.langyue.autotranslation.forge.smoke.SmokeTestFtb.run());
        Integrations.registerSmokeCheck("patchouli", "vazkii.patchouli.client.book.BookPage",
                () -> me.langyue.autotranslation.forge.smoke.SmokeTestPatchouli.run());
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