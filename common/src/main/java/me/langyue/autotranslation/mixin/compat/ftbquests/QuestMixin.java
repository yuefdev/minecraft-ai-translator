package me.langyue.autotranslation.mixin.compat.ftbquests;

import dev.ftb.mods.ftbquests.quest.Quest;
import me.langyue.autotranslation.tr.TranslationRuntime;
import me.langyue.autotranslation.tr.ftb.FtbQuestsSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Quest subtitle and description. The description keeps exactly one component per raw line, because FTB Quests
 * addresses lines and page breaks by index.
 */
@Pseudo
@Mixin(Quest.class)
public abstract class QuestMixin {
    @Shadow
    private String rawSubtitle;
    @Shadow
    @Final
    private List<String> rawDescription;

    @Unique
    private Component at$subtitle;
    @Unique
    private String at$subtitleRaw;
    @Unique
    private long at$subtitleGeneration = -1L;

    @Unique
    private List<Component> at$description;
    @Unique
    private List<String> at$descriptionRaw;
    @Unique
    private long at$descriptionGeneration = -1L;

    @Inject(method = "getSubtitle", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void at$translateSubtitle(CallbackInfoReturnable<Component> cir) {
        String raw = this.rawSubtitle;
        if (raw == null || raw.isEmpty() || !FtbQuestsSupport.enabled()) return;
        long generation = TranslationRuntime.generation();
        if (this.at$subtitleGeneration != generation || !raw.equals(this.at$subtitleRaw)) {
            this.at$subtitle = FtbQuestsSupport.subtitle((Quest) (Object) this, raw);
            this.at$subtitleRaw = raw;
            this.at$subtitleGeneration = generation;
        }
        if (this.at$subtitle != null) {
            cir.setReturnValue(this.at$subtitle);
        }
    }

    @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void at$translateDescription(CallbackInfoReturnable<List<Component>> cir) {
        if (this.rawDescription.isEmpty() || !FtbQuestsSupport.enabled()) return;
        long generation = TranslationRuntime.generation();
        // the editor changes the raw list in place, so compare contents and not only the generation
        if (this.at$descriptionGeneration != generation || !this.rawDescription.equals(this.at$descriptionRaw)) {
            this.at$description = FtbQuestsSupport.description((Quest) (Object) this, this.rawDescription);
            this.at$descriptionRaw = List.copyOf(this.rawDescription);
            this.at$descriptionGeneration = generation;
        }
        if (this.at$description != null) {
            cir.setReturnValue(this.at$description);
        }
    }
}
