package me.langyue.autotranslation.mc.smoke;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import me.langyue.autotranslation.mc.ftb.FtbQuestsSupport;
import me.langyue.autotranslation.core.smoke.SmokeReport;
import me.langyue.autotranslation.engine.Engine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        report.expectMethod(loader, "dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel$ChapterButton", "translateChapterSubtitle", "ftbquests: chapter button redirect applied");
        try {
            ClientQuestFile file = new ClientQuestFile();
            ChapterGroup group = new ChapterGroup(0L, file);
            Chapter chapter = new Chapter(0L, file, group);
            final Quest quest = new Quest(0L, chapter);
            // a quest that is not yet registered keeps its title next to the object (proto translation)
            quest.setRawTitle(ORIGINAL_TITLE);

            // The game's own title getter needs a client level (registries); outside a world the support layer that the
            // mixin calls is driven directly. The "mixin applied" checks above prove the wiring.
            report.check("ftbquests: nothing translated yet (pending)", FtbQuestsSupport.title(quest, ORIGINAL_TITLE) == null, "");
            report.waitFor("ftbquests: title translated through FTB Quests support -> pipeline -> provider",
                    () -> {
                        Component translated = FtbQuestsSupport.title(quest, ORIGINAL_TITLE);
                        return translated != null && translated.getString().startsWith("TR| Collect Iron Ingots");
                    }, 600,
                    () -> "no translation yet; last error unknown");
            report.waitFor("ftbquests: data untouched (raw text, colour style)",
                    () -> {
                        Component translated = FtbQuestsSupport.title(quest, ORIGINAL_TITLE);
                        if (translated == null) return false;
                        boolean gold = false;
                        for (Component sibling : translated.getSiblings()) {
                            if (sibling.getString().equals("Iron") && sibling.getStyle().getColor() != null
                                    && sibling.getStyle().getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.GOLD))) {
                                gold = true;
                            }
                        }
                        return quest.getRawTitle().equals(ORIGINAL_TITLE) && gold && translated.getString().startsWith("TR|");
                    }, 600,
                    () -> "raw title or colour style changed");
            checkInvalidRussianTranslation(report, quest);
        } catch (Throwable t) {
            report.check("ftbquests: functional test setup", false, t.toString());
        }
    }

    /** A stale cache or personal correction must not let an invalid Unicode escape crash the renderer. */
    private static void checkInvalidRussianTranslation(SmokeReport report, Quest quest) {
        Engine engine = Engine.get();
        String source = "Collect &6Copper&r Ingots";
        engine.corrections().putInMemory(source, "Соберите &6медь&r \\uZZZZ", null);
        engine.applyConfig();
        try {
            report.check("ftbquests: malformed Russian title falls back to original",
                    FtbQuestsSupport.title(quest, source) == null, "");
        } catch (RuntimeException e) {
            report.check("ftbquests: malformed Russian title falls back to original", false, e.toString());
        }
        try {
            report.check("ftbquests: malformed Russian subtitle falls back to original",
                    FtbQuestsSupport.subtitle(quest, source) == null, "");
        } catch (RuntimeException e) {
            report.check("ftbquests: malformed Russian subtitle falls back to original", false, e.toString());
        }
        try {
            report.check("ftbquests: malformed Russian description falls back to original",
                    FtbQuestsSupport.description(quest, java.util.Arrays.asList("", source, "{@pagebreak}")) == null, "");
        } catch (RuntimeException e) {
            report.check("ftbquests: malformed Russian description falls back to original", false, e.toString());
        }
        engine.corrections().putInMemory(source, "Соберите &6медь&r", null);
        engine.applyConfig();
        Component valid = FtbQuestsSupport.title(quest, source);
        report.check("ftbquests: valid Cyrillic translation still renders",
                valid != null && valid.getString().equals("Соберите медь"), "");
        java.util.List<String> lines = java.util.Arrays.asList("", source, "{@pagebreak}");
        java.util.List<Component> description = FtbQuestsSupport.description(quest, lines);
        Component pageBreak = dev.ftb.mods.ftbquests.util.TextUtils.parseRawText("{@pagebreak}",
                net.minecraft.data.registries.VanillaRegistries.createLookup());
        report.check("ftbquests: valid description keeps blank lines and page breaks",
                description != null && description.size() == lines.size()
                        && description.get(0).getString().isEmpty()
                        && description.get(1).getString().equals("Соберите медь")
                        && description.get(2).getString().equals(pageBreak.getString()),
                description == null ? "description unavailable" : description.toString());
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
