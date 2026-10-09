package me.langyue.autotranslation.mc.ftb;

import me.langyue.autotranslation.mc.Texts;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftblibrary.util.ClientTextComponentUtils;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.TextSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-time translation of FTB Quests text.
 *
 * <p>Quest data is never modified: titles, subtitles and descriptions are translated only when they are turned
 * into {@link Component}s for the screen, from the raw strings that FTB Quests keeps (and that the server sends to
 * the client). Ids, dependencies, tasks, rewards and progress are not touched.
 */
public final class FtbQuestsSupport {
    private FtbQuestsSupport() {
    }

    /** Translation is on for quests, and not while the player is editing the quest book. */
    public static boolean enabled() {
        Engine engine = Engine.get();
        if (engine == null || !engine.questsEnabled()) return false;
        if (engine.settings().pauseWhileEditingQuests) {
            try {
                if (ClientQuestFile.exists() && ClientQuestFile.INSTANCE.canEdit()) return false;
            } catch (Throwable ignored) {
                // never let a context check break the quest screen
            }
        }
        return true;
    }

    public static Component title(QuestObjectBase object, String raw) {
        Engine engine = Engine.get();
        engine.markHook("ftbquests.title");
        String translated = engine.translate(raw, content(object, "title", describe(object) + " title"), MaskProfile.FTB_RICH);
        return build(engine, translated, raw);
    }

    public static Component subtitle(Quest quest, String raw) {
        Engine engine = Engine.get();
        engine.markHook("ftbquests.subtitle");
        String translated = engine.translate(raw, content(quest, "subtitle", "quest subtitle"), MaskProfile.FTB_RICH);
        return build(engine, translated, raw);
    }

    /** Same size and order as {@code rawLines} (FTB Quests addresses lines and page breaks by index), or null. */
    public static Component[] description(Quest quest, List<String> rawLines) {
        Engine engine = Engine.get();
        engine.markHook("ftbquests.description");
        List<String> snapshot = new ArrayList<String>(rawLines);
        List<String> translated = engine.translateLines(snapshot, content(quest, "description", "quest description"), MaskProfile.FTB_RICH);
        if (translated == null) return null;
        Component[] result = new Component[snapshot.size()];
        for (int i = 0; i < result.length; i++) {
            String line = translated.get(i);
            result[i] = line.equals(snapshot.get(i)) ? ClientTextComponentUtils.parse(line) : build(engine, line, snapshot.get(i));
        }
        return result;
    }

    /** Chapter subtitle lines shown as the tooltip of the chapter list (display only; the data is untouched). */
    public static List<String> chapterSubtitle(Chapter chapter, List<String> raw) {
        if (!enabled() || raw == null || raw.isEmpty()) return raw;
        Engine engine = Engine.get();
        engine.markHook("ftbquests.chapter-subtitle");
        List<String> translated = engine.translateLines(new ArrayList<String>(raw),
                content(chapter, "subtitle", "chapter subtitle"), MaskProfile.FTB_RICH);
        return translated == null ? raw : translated;
    }

    private static Component build(Engine engine, String translated, String raw) {
        if (translated == null) return null;
        Component component = ClientTextComponentUtils.parse(translated);
        if (engine.showOriginal()) {
            component = component.copy().append(Texts.literal(" (" + TextSupport.plainFtb(raw) + ")")
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
            id = "ftbquests:" + object.getObjectType() + ":" + QuestObjectBase.getCodeString(object) + ":" + part;
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
        if (object instanceof Task) quest = ((Task) object).quest;
        else if (object instanceof Reward) quest = ((Reward) object).quest;
        else if (object instanceof Quest) quest = (Quest) object;
        if (quest != null && quest != object) append(sb, "quest", quest);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, QuestObjectBase object) {
        String raw = object.title;
        if (raw == null || raw.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append("; ");
        sb.append(label).append(": ").append(TextSupport.plainFtb(raw));
    }

    // -------------------------------------------------------------------------------------------- refresh

    public static void refreshOpenScreen() {
        Object screen = Minecraft.getInstance().screen;
        if (screen instanceof ScreenWrapper) {
            BaseScreen gui = ((ScreenWrapper) screen).getGui();
            if (gui != null) gui.refreshWidgets();
        }
    }
}
