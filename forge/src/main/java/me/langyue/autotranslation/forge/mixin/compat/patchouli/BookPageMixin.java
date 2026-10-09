package me.langyue.autotranslation.forge.mixin.compat.patchouli;

import me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.patchouli.client.book.BookPage;

/** Page titles and labels, which Patchouli resolves through {@code BookPage.i18n / i18nText}. */
@Pseudo
@Mixin(BookPage.class)
public abstract class BookPageMixin {
    @Inject(method = "i18n", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void at$translateLabel(String text, CallbackInfoReturnable<String> cir) {
        BookPage page = (BookPage) (Object) this;
        if (page.book == null) return;
        String translated = PatchouliSupport.translateLabel(page.book, cir.getReturnValue());
        if (translated != null) cir.setReturnValue(translated);
    }

    @Inject(method = "i18nText", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void at$translateLabelText(String text, CallbackInfoReturnable<Component> cir) {
        BookPage page = (BookPage) (Object) this;
        if (page.book == null || cir.getReturnValue() == null) return;
        String translated = PatchouliSupport.translateLabel(page.book, cir.getReturnValue().getString());
        if (translated != null) cir.setReturnValue(Component.literal(translated).setStyle(cir.getReturnValue().getStyle()));
    }
}
