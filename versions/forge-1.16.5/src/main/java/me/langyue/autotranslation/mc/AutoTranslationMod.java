package me.langyue.autotranslation.mc;

import org.apache.commons.lang3.tuple.Pair;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.network.FMLNetworkConstants;

/** Entry point. The translator is a pure client side mod: nothing is started on a dedicated server. */
@Mod(AutoTranslationMod.MOD_ID)
public final class AutoTranslationMod {
    public static final String MOD_ID = "autotranslation";

    public AutoTranslationMod() {
        // the mod must not make servers without it show up as incompatible
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init();
        }
    }
}
