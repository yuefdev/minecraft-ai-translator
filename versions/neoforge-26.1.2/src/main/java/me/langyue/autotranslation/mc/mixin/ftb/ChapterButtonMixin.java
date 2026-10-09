package me.langyue.autotranslation.mc.mixin.ftb;

import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.quest.Chapter;
import me.langyue.autotranslation.mc.ftb.FtbQuestsSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The chapter list shows the chapter subtitle as a tooltip. Only this one read is redirected; the chapter's own
 * raw subtitle list (which the editor modifies) is left alone.
 */
@Pseudo
@Mixin(ChapterPanel.ChapterButton.class)
public abstract class ChapterButtonMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/Chapter;getRawSubtitle()Ljava/util/List;"), remap = false, require = 0)
    private List<String> at$translateChapterSubtitle(Chapter chapter) {
        return FtbQuestsSupport.chapterSubtitle(chapter, chapter.getRawSubtitle());
    }
}
