package me.langyue.autotranslation.mc.mixin.ftb;

import dev.ftb.mods.ftbquests.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.quest.Chapter;
import me.langyue.autotranslation.mc.ftb.FtbQuestsSupport;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The chapter list shows the chapter subtitle as a tooltip. Only this one read is redirected; the chapter's own
 * subtitle list (which the editor modifies) is left alone.
 */
@Pseudo
@Mixin(ChapterPanel.ChapterButton.class)
public abstract class ChapterButtonMixin {
    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Ldev/ftb/mods/ftbquests/quest/Chapter;subtitle:Ljava/util/List;"), remap = false, require = 0)
    private List<String> at$translateChapterSubtitle(Chapter chapter) {
        return FtbQuestsSupport.chapterSubtitle(chapter, chapter.subtitle);
    }
}
