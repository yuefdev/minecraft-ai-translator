package me.langyue.autotranslation.mc.smoke;

import dev.ftb.mods.ftbquests.quest.Quest;
import me.langyue.autotranslation.core.smoke.SmokeReport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FTB Quests part of the start-up check. Kept in its own class so that it is only loaded when the mod is installed
 * (the JVM verifier would otherwise try to load the optional mod's classes while verifying {@link SmokeTest}).
 */
final class SmokeFtb {
    private static final String ORIGINAL_TITLE = "Collect &6Iron&r Ingots";

    private SmokeFtb() {
    }

    static void run(SmokeReport report) {
        ClassLoader loader = SmokeFtb.class.getClassLoader();
        report.expectField(loader, "dev.ftb.mods.ftbquests.quest.QuestObjectBase", "at$title", "ftbquests: QuestObjectBase mixin applied");
        report.expectField(loader, "dev.ftb.mods.ftbquests.quest.Quest", "at$description", "ftbquests: Quest mixin applied");
        report.expectMethod(loader, "dev.ftb.mods.ftbquests.gui.quests.ChapterPanel$ChapterButton", "translateChapterSubtitle", "ftbquests: chapter button redirect applied");
        try {
            // a quest without its constructor: only the text fields the hooks read are filled in
            final Quest quest = allocate(Quest.class);
            set(quest, "title", ORIGINAL_TITLE);
            set(quest, "subtitle", "A short subtitle for this quest");
            final List<String> lines = new ArrayList<String>(Arrays.asList(
                    "Mine some iron ore and smelt it in a furnace.", "{@pagebreak}", "Bring 25 ingots back to the village."));
            set(quest, "description", lines);
            final List<String> expectedLines = new ArrayList<String>(lines);

            Component first = quest.getTitle();
            report.check("ftbquests: original title is shown while translation is pending",
                    first.getString().contains("Collect") && !first.getString().startsWith("TR|"), first.getString());
            report.waitFor("ftbquests: title translated through FTB Quests -> pipeline -> provider",
                    () -> quest.getTitle().getString().startsWith("TR| Collect Iron Ingots"), 600,
                    () -> "title stayed: " + quest.getTitle().getString());
            report.waitFor("ftbquests: description translated with page break and line count preserved",
                    () -> {
                        Component[] result = quest.getDescription();
                        return result.length == 3 && result[0].getString().startsWith("TR| Mine some iron")
                                && result[1].getString().equals("{@pagebreak}")
                                && result[2].getString().startsWith("TR| Bring 25 ingots");
                    }, 600,
                    () -> {
                        StringBuilder sb = new StringBuilder();
                        for (Component c : quest.getDescription()) sb.append('[').append(c.getString()).append(']');
                        return sb.toString();
                    });
            report.waitFor("ftbquests: subtitle translated",
                    () -> quest.getSubtitle().getString().startsWith("TR| A short subtitle"), 600,
                    () -> quest.getSubtitle().getString());
            report.waitFor("ftbquests: data untouched (raw text, colour style)",
                    () -> {
                        boolean raw = quest.title.equals(ORIGINAL_TITLE)
                                && quest.subtitle.equals("A short subtitle for this quest")
                                && quest.description.equals(expectedLines);
                        boolean gold = false;
                        for (Component sibling : quest.getTitle().getSiblings()) {
                            if (sibling.getString().equals("Iron") && sibling.getStyle().getColor() != null
                                    && sibling.getStyle().getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.GOLD))) {
                                gold = true;
                            }
                        }
                        return raw && gold && quest.getTitle().getString().startsWith("TR|");
                    }, 600,
                    () -> quest.getTitle().toString());
        } catch (Throwable t) {
            report.check("ftbquests: functional test setup", false, t.toString());
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) allocate.invoke(unsafe, type);
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
