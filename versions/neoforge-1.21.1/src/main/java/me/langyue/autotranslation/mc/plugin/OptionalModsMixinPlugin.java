package me.langyue.autotranslation.mc.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the FTB Quests / Patchouli mixins only when those mods are installed, so the game starts without them. */
public final class OptionalModsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.ftb.")) return isModPresent("ftbquests");
        if (mixinClassName.contains(".mixin.patchouli.")) return isModPresent("patchouli");
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

    /** The loading mod list exists this early (before any mod object); reached by reflection. */
    private static boolean isModPresent(String modId) {
        Object list = null;
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            list = fmlLoader.getMethod("getLoadingModList").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through to the other accessor
        }
        if (list == null) {
            try {
                Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList");
                list = loadingModList.getMethod("get").invoke(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        try {
            return list != null && list.getClass().getMethod("getModFileById", String.class).invoke(list, modId) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
