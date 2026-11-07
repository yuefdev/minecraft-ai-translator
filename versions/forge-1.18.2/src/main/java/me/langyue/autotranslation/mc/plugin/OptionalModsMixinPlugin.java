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

    /** Forge's loading mod list, which exists this early (before any mod object), reached by reflection. */
    private static boolean isModPresent(String modId) {
        Object fabric = fabricLoader();
        if (fabric != null) {
            try {
                return (Boolean) fabric.getClass().getMethod("isModLoaded", String.class).invoke(fabric, modId);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        try {
            Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object list = fmlLoader.getMethod("getLoadingModList").invoke(null);
            return list != null && list.getClass().getMethod("getModFileById", String.class).invoke(list, modId) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Fabric Loader, when the mod runs on Fabric; reached by reflection so that Forge builds do not need it. */
    private static Object fabricLoader() {
        try {
            return Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
