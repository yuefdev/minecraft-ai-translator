package me.langyue.autotranslation.mc.mixin.ftb;

import dev.ftb.mods.ftbquests.quest.Quest;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.mc.ftb.FtbQuestsSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest subtitle and description. The description keeps exactly one component per raw line, because FTB Quests
 * addresses lines and page breaks by index.
 */
@Pseudo
@Mixin(Quest.class)
public abstract class QuestMixin {
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
        Quest self = (Quest) (Object) this;
        String raw = self.getRawSubtitle();
        if (raw == null || raw.isEmpty() || !FtbQuestsSupport.enabled()) return;
        long generation = Engine.get().generation();
        if (this.at$subtitleGeneration != generation || !raw.equals(this.at$subtitleRaw)) {
            this.at$subtitle = FtbQuestsSupport.subtitle(self, raw);
            this.at$subtitleRaw = raw;
            this.at$subtitleGeneration = generation;
        }
        if (this.at$subtitle != null) {
            cir.setReturnValue(this.at$subtitle);
        }
    }

    @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void at$translateDescription(CallbackInfoReturnable<List<Component>> cir) {
        Quest self = (Quest) (Object) this;
        List<String> raw = self.getRawDescription();
        if (raw == null || raw.isEmpty() || !FtbQuestsSupport.enabled()) return;
        long generation = Engine.get().generation();
        // the editor changes the raw list in place, so compare contents and not only the generation
        if (this.at$descriptionGeneration != generation || !raw.equals(this.at$descriptionRaw)) {
            this.at$description = FtbQuestsSupport.description(self, raw);
            this.at$descriptionRaw = new ArrayList<String>(raw);
            this.at$descriptionGeneration = generation;
        }
        if (this.at$description != null) {
            cir.setReturnValue(this.at$description);
        }
    }
}
