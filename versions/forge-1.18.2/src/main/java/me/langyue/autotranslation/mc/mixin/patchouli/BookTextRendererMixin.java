package me.langyue.autotranslation.mc.mixin.patchouli;

import me.langyue.autotranslation.mc.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vazkii.patchouli.common.book.Book;

/**
 * Translates page text right before Patchouli parses and lays it out, so that wrapping, paging and scaling are
 * computed for the Turkish text instead of being patched up afterwards.
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.gui.BookTextRenderer")
public abstract class BookTextRendererMixin {
    @Shadow(remap = false)
    @Final
    private Book book;

    @ModifyArg(method = "<init>(Lvazkii/patchouli/client/book/gui/GuiBook;Lnet/minecraft/network/chat/Component;IIIII)V",
            at = @At(value = "INVOKE",
                    target = "Lvazkii/patchouli/client/book/text/BookTextParser;parse(Lnet/minecraft/network/chat/Component;)Ljava/util/List;"),
            remap = false, require = 0)
    private Component at$translateBeforeLayout(Component text) {
        return PatchouliSupport.translatePageText(this.book, text);
    }
}
