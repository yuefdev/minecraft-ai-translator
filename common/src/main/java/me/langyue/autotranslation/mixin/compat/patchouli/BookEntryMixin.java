package me.langyue.autotranslation.mixin.compat.patchouli;

import me.langyue.autotranslation.tr.patchouli.PatchouliSupport;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.patchouli.client.book.BookEntry;

/** Entry names, for books that use plain text names as well as books whose language keys resolve to English. */
@Pseudo
@Mixin(BookEntry.class)
public abstract class BookEntryMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void at$translateName(CallbackInfoReturnable<MutableComponent> cir) {
        BookEntry entry = (BookEntry) (Object) this;
        MutableComponent translated = PatchouliSupport.translateName(entry.getBook(), "entry", entry.getId().toString(), cir.getReturnValue());
        if (translated != null) cir.setReturnValue(translated);
    }
}
