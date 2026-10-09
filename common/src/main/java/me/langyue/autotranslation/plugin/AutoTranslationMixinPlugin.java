package me.langyue.autotranslation.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the optional FTB Quests / Patchouli mixins only when those mods are installed, so the game starts
 * normally without them.
 */
public final class AutoTranslationMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MOD_BY_PACKAGE = Map.of(
            "me.langyue.autotranslation.mixin.compat.ftbquests.", "ftbquests",
            "me.langyue.autotranslation.mixin.compat.patchouli.", "patchouli"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> entry : REQUIRED_MOD_BY_PACKAGE.entrySet()) {
            if (mixinClassName.startsWith(entry.getKey())) {
                return isModPresent(entry.getValue());
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /**
     * Platform independent mod detection that works this early (before any mod object exists): Forge's loading
     * mod list, or Fabric's loader, reached by reflection.
     */
    private static boolean isModPresent(String modId) {
        try {
            Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object list = fmlLoader.getMethod("getLoadingModList").invoke(null);
            if (list != null) {
                return list.getClass().getMethod("getModFileById", String.class).invoke(list, modId) != null;
            }
        } catch (ClassNotFoundException ignored) {
            // not running on Forge
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            return (Boolean) fabricLoader.getMethod("isModLoaded", String.class).invoke(instance, modId);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
