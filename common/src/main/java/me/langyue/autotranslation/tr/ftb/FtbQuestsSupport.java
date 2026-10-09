package me.langyue.autotranslation.tr.ftb;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.util.TextUtils;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.tr.TextSupport;
import me.langyue.autotranslation.tr.TranslationBridge;
import me.langyue.autotranslation.tr.TranslationRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-time translation of FTB Quests text.
 *
 * <p>Quest data is never modified: titles, subtitles and descriptions are translated only when they are turned
 * into {@link Component}s for the screen, from the raw strings that FTB Quests keeps (and that the server sends
 * to the client). Ids, dependencies, tasks, rewards and progress are not touched. The raw getters that the quest
 * editor uses ({@code getRawTitle}, {@code getRawDescription}, ...) are deliberately not hooked.
 */
public final class FtbQuestsSupport {
    private FtbQuestsSupport() {
    }

    /** Translation is on for quests, and not while the player is editing the quest book. */
    public static boolean enabled() {
        if (!TranslationRuntime.questsEnabled()) return false;
        if (AutoTranslation.CONFIG.turkish.pauseWhileEditingQuests) {
            try {
                if (ClientQuestFile.exists() && ClientQuestFile.INSTANCE.canEdit()) return false;
            } catch (Throwable ignored) {
                // never let a context check break the quest screen
            }
        }
        return true;
    }

    public static Component title(QuestObjectBase object, String raw) {
        TranslationRuntime.markHook("ftbquests.title");
        String translated = TranslationBridge.translate(raw, content(object, "title", describe(object) + " title"), MaskProfile.FTB_RICH);
        return build(translated, raw);
    }

    public static Component subtitle(Quest quest, String raw) {
        TranslationRuntime.markHook("ftbquests.subtitle");
        String translated = TranslationBridge.translate(raw, content(quest, "subtitle", "quest subtitle"), MaskProfile.FTB_RICH);
        return build(translated, raw);
    }

    /**
     * Same size and order as {@code rawLines} (FTB Quests addresses lines and page breaks by index), or null.
     */
    public static List<Component> description(Quest quest, List<String> rawLines) {
        TranslationRuntime.markHook("ftbquests.description");
        List<String> snapshot = new ArrayList<>(rawLines);
        List<String> translated = TranslationBridge.translateLines(snapshot, content(quest, "description", "quest description"), MaskProfile.FTB_RICH);
        if (translated == null) return null;
        List<Component> result = new ArrayList<>(snapshot.size());
        for (int i = 0; i < snapshot.size(); i++) {
            String line = translated.get(i);
            result.add(line.equals(snapshot.get(i)) ? TextUtils.parseRawText(line) : build(line, snapshot.get(i)));
        }
        return List.copyOf(result);
    }

    /** Chapter subtitle lines shown as the tooltip of the chapter list (display only; the data is untouched). */
    public static List<String> chapterSubtitle(Chapter chapter, List<String> raw) {
        if (!enabled() || raw == null || raw.isEmpty()) return raw;
        TranslationRuntime.markHook("ftbquests.chapter-subtitle");
        List<String> translated = TranslationBridge.translateLines(new ArrayList<>(raw),
                content(chapter, "subtitle", "chapter subtitle"), MaskProfile.FTB_RICH);
        return translated == null ? raw : translated;
    }

    private static Component build(String translated, String raw) {
        if (translated == null) return null;
        Component component = TextUtils.parseRawText(translated);
        if (TranslationRuntime.showOriginal()) {
            component = component.copy().append(Component.literal(" (" + TextSupport.plainFtb(raw) + ")")
                    .withStyle(ChatFormatting.GRAY));
        }
        return component;
    }

    private static String describe(QuestObjectBase object) {
        if (object instanceof Chapter) return "chapter";
        if (object instanceof Quest) return "quest";
        if (object instanceof Task) return "quest task";
        if (object instanceof Reward) return "quest reward";
        return "quest object";
    }

    private static Content content(QuestObjectBase object, String part, String contentType) {
        String id;
        String context;
        try {
            id = "ftbquests:" + object.getObjectType().getId() + ":" + object.getCodeString() + ":" + part;
            context = contextOf(object);
        } catch (Throwable t) {
            id = "ftbquests:" + object.getClass().getSimpleName() + ":" + part;
            context = "";
        }
        return new Content("ftbquests", contentType, id, context);
    }

    /** Chapter and quest titles that help the model pick the right sense of a word. */
    private static String contextOf(QuestObjectBase object) {
        StringBuilder sb = new StringBuilder();
        Chapter chapter = object.getQuestChapter();
        if (chapter != null && chapter != object) append(sb, "chapter", chapter);
        Quest quest = null;
        if (object instanceof Task task) quest = task.getQuest();
        else if (object instanceof Reward reward) quest = reward.getQuest();
        else if (object instanceof Quest q) quest = q;
        if (quest != null && quest != object) append(sb, "quest", quest);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, QuestObjectBase object) {
        String raw = object.getRawTitle();
        if (raw == null || raw.isBlank()) return;
        if (!sb.isEmpty()) sb.append("; ");
        sb.append(label).append(": ").append(TextSupport.plainFtb(raw));
    }

    // -------------------------------------------------------------------------------------------- refresh

    public static void refreshOpenScreen() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof QuestScreen questScreen) {
            questScreen.refreshChapterPanel();
            questScreen.refreshQuestPanel();
            if (questScreen.isViewingQuest()) questScreen.refreshViewQuestPanel();
        }
    }
}
