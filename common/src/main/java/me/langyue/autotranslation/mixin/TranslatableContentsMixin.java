package me.langyue.autotranslation.mixin;

import me.langyue.autotranslation.tr.TranslationRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears {@code TranslatableContents}' cached decomposition whenever a new async translation arrives so reused UI
 * components refresh cleanly without waiting for the screen to be reconstructed.
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    @Shadow
    private Language decomposedWith;

    @Unique
    private long at$generation = -1L;

    @Inject(method = "decompose()V", at = @At("HEAD"), require = 0)
    private void at$refreshOnTranslationArrival(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getOverlay() != null) return;
        if (!TranslationRuntime.usesPipeline() || !TranslationRuntime.genericEnabled()) return;
        long gen = TranslationRuntime.generation();
        if (this.at$generation != gen) {
            this.at$generation = gen;
            this.decomposedWith = null;
        }
    }
}
