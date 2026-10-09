package me.langyue.autotranslation.mc.mixin.generic;

import me.langyue.autotranslation.mc.generic.GenericText;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hard-coded text: a mod that writes {@code new TextComponent("Open the chest")} shows the Turkish text while a screen
 * is open. The component keeps its English text; only what it hands to the renderer changes.
 */
@Mixin(TextComponent.class)
public abstract class TextComponentMixin {
    @ModifyArg(method = "visitSelf(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/FormattedText$ContentConsumer;accept(Ljava/lang/String;)Ljava/util/Optional;"),
            index = 0, require = 0)
    private String at$translatePlain(String text) {
        return GenericText.translate(text);
    }

    @ModifyArg(method = "visitSelf(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;accept(Lnet/minecraft/network/chat/Style;Ljava/lang/String;)Ljava/util/Optional;"),
            index = 1, require = 0)
    private String at$translateStyled(String text) {
        return GenericText.translate(text);
    }
}
