package me.langyue.autotranslation.fabric;

import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.ScreenTranslationHelper;
import me.langyue.autotranslation.tr.Integrations;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class AutoTranslationFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Optional integrations. They are the same classes as in the Forge module (FTB Quests and Patchouli use the
        // same class names on both loaders); the lambdas only touch them when the mod is installed.
        Integrations.register("ftbquests", () -> me.langyue.autotranslation.forge.tr.ftb.FtbQuestsBootstrap.init());
        Integrations.register("patchouli", () -> me.langyue.autotranslation.forge.tr.patchouli.PatchouliBootstrap.init());
        Integrations.registerSmokeCheck("ftbquests", "dev.ftb.mods.ftbquests.quest.QuestObjectBase",
                () -> me.langyue.autotranslation.forge.smoke.SmokeTestFtb.run());
        Integrations.registerSmokeCheck("patchouli", "vazkii.patchouli.client.book.BookPage",
                () -> me.langyue.autotranslation.forge.smoke.SmokeTestPatchouli.run());
        AutoTranslation.init();
        // Mod Menu's mod list, like Forge's ModListScreen (by name: Mod Menu is optional)
        ScreenTranslationHelper.addScreenBlacklist("com.terraformersmc.modmenu.gui.ModsScreen");
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> AutoTranslation.stop());
    }
}
