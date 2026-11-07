package me.langyue.autotranslation.forge.mixin.compat.patchouli;

import me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vazkii.patchouli.common.book.Book;

/**
 * Translates page text right before Patchouli parses and lays it out, so that wrapping, paging and scaling are
 * computed for the Turkish text instead of being patched up afterwards.
 * <p>
 * Hooked on {@code BookTextParser.parse} rather than on the {@code BookTextRenderer} constructor that calls it: in a
 * real Fabric install the constructor selector either matched nothing or was narrowed by the mixin remapper to the
 * wrong constructor, so the hook silently did nothing. {@code parse} is unique and its selector needs no Minecraft
 * type, so it resolves the same way on Forge, NeoForge and Fabric, in development and in production.
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.text.BookTextParser")
public abstract class BookTextParserMixin {
    @Shadow(remap = false)
    @Final
    private Book book;

    @ModifyVariable(method = "parse", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private Component at$translateBeforeLayout(Component text) {
        return PatchouliSupport.translatePageText(this.book, text);
    }
}
