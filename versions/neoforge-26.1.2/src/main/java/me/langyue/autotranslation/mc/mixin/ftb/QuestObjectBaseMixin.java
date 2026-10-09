package me.langyue.autotranslation.mc.mixin.ftb;

import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.mc.ftb.FtbQuestsSupport;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Titles of chapters, quests, tasks, rewards and reward tables. Only the display component is replaced; the raw
 * title string that is saved and synchronised with the server is never changed.
 */
@Pseudo
@Mixin(QuestObjectBase.class)
public abstract class QuestObjectBaseMixin {
    @Unique
    private Component at$title;
    @Unique
    private String at$titleRaw;
    @Unique
    private long at$titleGeneration = -1L;

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void at$translateTitle(CallbackInfoReturnable<Component> cir) {
        QuestObjectBase self = (QuestObjectBase) (Object) this;
        String raw = self.getRawTitle();
        if (raw == null || raw.isEmpty() || !FtbQuestsSupport.enabled()) return;
        long generation = Engine.get().generation();
        if (this.at$titleGeneration != generation || !raw.equals(this.at$titleRaw)) {
            this.at$title = FtbQuestsSupport.title(self, raw);
            this.at$titleRaw = raw;
            this.at$titleGeneration = generation;
        }
        if (this.at$title != null) {
            cir.setReturnValue(this.at$title);
        }
    }
}
