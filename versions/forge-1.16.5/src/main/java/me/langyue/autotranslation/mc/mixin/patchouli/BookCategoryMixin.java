package me.langyue.autotranslation.mc.mixin.patchouli;

import me.langyue.autotranslation.mc.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.patchouli.client.book.BookCategory;

/** Category names. */
@Pseudo
@Mixin(BookCategory.class)
public abstract class BookCategoryMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void at$translateName(CallbackInfoReturnable<Component> cir) {
        BookCategory category = (BookCategory) (Object) this;
        MutableComponent translated = PatchouliSupport.translateName(category.getBook(), "category", category.getId().toString(), cir.getReturnValue());
        if (translated != null) cir.setReturnValue(translated);
    }
}
