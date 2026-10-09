package me.langyue.autotranslation.legacy;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Loading plugin ("coremod") of the 1.12.2 version. Instead of Mixin it uses a tiny ASM transformer that puts a
 * call to {@link Hooks} at the start of three text methods of the optional mods (BetterQuesting, FTB Quests and
 * Patchouli). Classes of mods that are not installed are never loaded, so nothing happens for them.
 */
@IFMLLoadingPlugin.Name("AutoTranslationCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"me.langyue.autotranslation."})
public class CorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.langyue.autotranslation.legacy.Transformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
