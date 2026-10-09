package me.langyue.autotranslation.mc.mixin.generic;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.mc.generic.GenericGate;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-coded text: a mod that writes {@code Component.literal("Open the chest")} shows the Turkish text while a
 * screen is open. The component itself keeps its English contents; only what it reports to the renderer changes.
 */
@Mixin(MutableComponent.class)
public abstract class MutableComponentMixin {
    @Unique
    private static final Content AT$CONTENT = new Content("game", "interface text", "ui", "");

    @Shadow
    @Final
    private ComponentContents contents;

    @Unique
    private ComponentContents at$translated;
    @Unique
    private String at$source;
    @Unique
    private long at$generation = -1L;

    @Inject(method = "getContents()Lnet/minecraft/network/chat/ComponentContents;", at = @At("HEAD"), cancellable = true, require = 0)
    private void at$translateLiteral(CallbackInfoReturnable<ComponentContents> cir) {
        if (!(this.contents instanceof PlainTextContents)) return;
        Engine engine = Engine.get();
        if (engine == null || !engine.genericEnabled() || !GenericGate.screenAllowed()) return;
        String text = ((PlainTextContents) this.contents).text();
        long generation = engine.generation();
        if (this.at$generation != generation || !text.equals(this.at$source)) {
            String translated = text.length() < 2 ? null : engine.translate(text, AT$CONTENT, MaskProfile.PLAIN);
            this.at$translated = translated == null ? null : PlainTextContents.create(translated);
            this.at$source = text;
            this.at$generation = generation;
        }
        if (this.at$translated != null) cir.setReturnValue(this.at$translated);
    }
}
