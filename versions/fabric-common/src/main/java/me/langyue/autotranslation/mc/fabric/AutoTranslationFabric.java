package me.langyue.autotranslation.mc.fabric;

import me.langyue.autotranslation.mc.ClientInit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entry point. Shared by every Fabric module under versions/; the rest of the mod is the same code as the
 * Forge/NeoForge module of that Minecraft version. Needs only Fabric Loader (no Fabric API).
 */
public final class AutoTranslationFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        ClientInit.init(loader.getGameDir(), loader::isModLoaded, ClientTicks::add);
    }
}
