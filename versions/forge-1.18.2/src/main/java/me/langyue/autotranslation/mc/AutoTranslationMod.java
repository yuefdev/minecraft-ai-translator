package me.langyue.autotranslation.mc;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;

/** Entry point. The translator is a pure client side mod: nothing is started on a dedicated server. */
@Mod(AutoTranslationMod.MOD_ID)
public final class AutoTranslationMod {
    public static final String MOD_ID = "autotranslation";

    public AutoTranslationMod() {
        // the mod must not make servers without it show up as incompatible
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init();
        }
    }
}
