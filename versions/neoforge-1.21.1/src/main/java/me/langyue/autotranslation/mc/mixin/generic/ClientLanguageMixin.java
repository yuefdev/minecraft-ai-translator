package me.langyue.autotranslation.mc.mixin.generic;

import me.langyue.autotranslation.mc.generic.GenericLanguage;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every language-file lookup of every mod ends here. */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true, require = 0)
    private void at$translate(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
        String value = cir.getReturnValue();
        if (value == null || value.equals(key) || value.equals(defaultValue)) return; // key without a value: nothing to translate
        String translated = GenericLanguage.translate(key, value);
        if (translated != null) cir.setReturnValue(translated);
    }
}
