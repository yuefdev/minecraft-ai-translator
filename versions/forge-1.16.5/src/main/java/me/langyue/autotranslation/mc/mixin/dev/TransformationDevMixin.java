package me.langyue.autotranslation.mc.mixin.dev;

import com.mojang.math.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Development runs only (never applied in a real game, see OptionalModsMixinPlugin). Forge 1.16.5 moves
 * {@code Transformation.compose/inverse} into an extension interface and keeps the vanilla bodies as
 * {@code composeVanilla/inverseVanilla}; the mojmap-named development game jar is left with call sites to the SRG
 * names and nothing that answers them, so model baking fails before the title screen. These two methods answer them.
 */
@Mixin(value = Transformation.class, remap = false)
public abstract class TransformationDevMixin {
    @Shadow
    private Transformation composeVanilla(Transformation other) {
        throw new AssertionError();
    }

    @Shadow
    private Transformation inverseVanilla() {
        throw new AssertionError();
    }

    public Transformation func_227985_a_(Transformation other) {
        return this.composeVanilla(other);
    }

    public Transformation func_227987_b_() {
        return this.inverseVanilla();
    }
}
