package me.langyue.autotranslation.mc.fabric.mixin;

import me.langyue.autotranslation.mc.fabric.ClientTicks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** End of the client tick, the same point Forge's ClientTickEvent (END/Post) fires at. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void autotranslation$endClientTick(CallbackInfo ci) {
        ClientTicks.run();
    }
}
