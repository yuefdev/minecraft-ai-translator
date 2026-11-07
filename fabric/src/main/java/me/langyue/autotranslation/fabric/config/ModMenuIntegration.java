package me.langyue.autotranslation.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.langyue.autotranslation.gui.TranslatorSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Opens the same settings screen as the Forge "Config" button. */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TranslatorSettingsScreen::new;
    }
}
