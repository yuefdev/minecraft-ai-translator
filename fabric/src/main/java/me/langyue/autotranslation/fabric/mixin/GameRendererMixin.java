package me.langyue.autotranslation.fabric.mixin;

import me.langyue.autotranslation.ScreenTranslationHelper;
import me.langyue.autotranslation.gui.widgets.AutoTranslationIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /*
     * @ModifyArg, not @ModifyArgs: @ModifyArgs generates a synthetic Args class at the call site, and the local variable
     * analysis of other mods' injectors in the same method (Fabric API's screen events capture locals here) cannot
     * resolve that class. The handler takes every argument of the call and hands back the first one unchanged.
     */
    @ModifyArg(method = "render(FJZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            ),
            index = 0
    )
    private GuiGraphics renderScreenPost(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ScreenTranslationHelper.ready();
        AutoTranslationIcon.setArgs(guiGraphics, mouseX, mouseY, partialTick);
        return guiGraphics;
    }
}
