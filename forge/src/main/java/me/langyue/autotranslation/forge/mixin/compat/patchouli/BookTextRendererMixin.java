package me.langyue.autotranslation.forge.mixin.compat.patchouli;

import me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vazkii.patchouli.client.book.gui.BookTextRenderer;
import vazkii.patchouli.common.book.Book;

/**
 * Translates page text right before Patchouli parses and lays it out, so that wrapping, paging and scaling are
 * computed for the Turkish text instead of being patched up afterwards.
 */
@Pseudo
@Mixin(BookTextRenderer.class)
public abstract class BookTextRendererMixin {
    @Shadow
    @Final
    private Book book;

    // Matched by name only: a descriptor would contain Minecraft's Component, whose runtime name differs between
    // Forge (Mojang names) and Fabric (intermediary), and remap = false keeps it from being remapped. Only the full
    // constructor calls parse; the short one delegates to it.
    @ModifyArg(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lvazkii/patchouli/client/book/text/BookTextParser;parse"),
            remap = false, require = 0)
    private Component at$translateBeforeLayout(Component text) {
        return PatchouliSupport.translatePageText(this.book, text);
    }
}
