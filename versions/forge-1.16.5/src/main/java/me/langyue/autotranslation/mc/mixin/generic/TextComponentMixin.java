package me.langyue.autotranslation.mc.mixin.generic;

import me.langyue.autotranslation.mc.generic.GenericText;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-coded text: a mod that writes {@code new TextComponent("Open the chest")} shows the Turkish text while a screen
 * is open. The component keeps its English text; the rendering code reads the text through {@code getContents()}
 * (BaseComponent's visit methods), so that is the one place that changes.
 */
@Mixin(TextComponent.class)
public abstract class TextComponentMixin {
    @Inject(method = "getContents()Ljava/lang/String;", at = @At("RETURN"), cancellable = true, require = 0)
    private void at$translate(CallbackInfoReturnable<String> cir) {
        String text = cir.getReturnValue();
        String translated = GenericText.translate(text);
        if (translated != text) cir.setReturnValue(translated);
    }
}
