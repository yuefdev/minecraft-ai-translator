package me.langyue.autotranslation.forge.smoke;

import me.langyue.autotranslation.tr.TranslationRuntime;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * FTB Quests part of the start-up check. Kept in its own class so that it is only loaded when the mod is installed (the JVM verifier would otherwise
 * try to load the optional mod's classes while verifying {@link SmokeTest}).
 */
final class SmokeTestFtb {
    private static final String ORIGINAL_TITLE = "Collect &6Iron&r Ingots";

    private SmokeTestFtb() {
    }

    static void run() {
        me.langyue.autotranslation.smoke.SmokeTest.expectField("dev.ftb.mods.ftbquests.quest.QuestObjectBase", "at$title", "ftbquests: QuestObjectBase mixin applied");
        me.langyue.autotranslation.smoke.SmokeTest.expectField("dev.ftb.mods.ftbquests.quest.Quest", "at$description", "ftbquests: Quest mixin applied");
        me.langyue.autotranslation.smoke.SmokeTest.expectMethod("dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel$ChapterButton", "translateChapterSubtitle", "ftbquests: chapter button redirect applied");
        try {
            dev.ftb.mods.ftbquests.client.ClientQuestFile file = new dev.ftb.mods.ftbquests.client.ClientQuestFile();
            var group = new dev.ftb.mods.ftbquests.quest.ChapterGroup(0x10L, file);
            var chapter = new dev.ftb.mods.ftbquests.quest.Chapter(0x20L, file, group);
            chapter.setRawTitle("Getting Started");
            var quest = new dev.ftb.mods.ftbquests.quest.Quest(0x30L, chapter);
            quest.setRawTitle(ORIGINAL_TITLE);
            quest.setRawSubtitle("A short subtitle for this quest");
            quest.getRawDescription().add("Mine some iron ore and smelt it in a furnace.");
            quest.getRawDescription().add("{@pagebreak}");
            quest.getRawDescription().add("Bring 25 ingots back to the village.");
            String codeBefore = quest.getCodeString();
            Component first = quest.getTitle();
            me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: original title is shown while translation is pending",
                    first.getString().contains("Collect") && !first.getString().startsWith("TR|"), first.getString());
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("ftbquests: title translated through FTB Quests -> pipeline -> provider",
                    () -> quest.getTitle().getString().startsWith("TR| Collect Iron Ingots"), 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: title translated through FTB Quests -> pipeline -> provider", false,
                            "title stayed: " + quest.getTitle().getString() + " | last error: " + TranslationRuntime.service().stats().lastError())));
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("ftbquests: description translated with page break and line count preserved",
                    () -> {
                        var lines = quest.getDescription();
                        return lines.size() == 3 && lines.get(0).getString().startsWith("TR| Mine some iron")
                                && lines.get(1).getString().equals("{@pagebreak}")
                                && lines.get(2).getString().startsWith("TR| Bring 25 ingots");
                    }, 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: description translated with page break and line count preserved", false,
                            quest.getDescription().stream().map(Component::getString).toList().toString())));
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("ftbquests: subtitle translated",
                    () -> quest.getSubtitle().getString().startsWith("TR| A short subtitle"), 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: subtitle translated", false, quest.getSubtitle().getString())));
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("ftbquests: data untouched (raw text, id, colour style)",
                    () -> {
                        boolean raw = quest.getRawTitle().equals(ORIGINAL_TITLE)
                                && quest.getRawSubtitle().equals("A short subtitle for this quest")
                                && quest.getRawDescription().equals(List.of("Mine some iron ore and smelt it in a furnace.", "{@pagebreak}", "Bring 25 ingots back to the village."))
                                && quest.getCodeString().equals(codeBefore);
                        boolean gold = quest.getTitle().getSiblings().stream().anyMatch(s ->
                                s.getString().equals("Iron") && s.getStyle().getColor() != null
                                        && s.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD)));
                        return raw && gold && quest.getTitle().getString().startsWith("TR|");
                    }, 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: data untouched (raw text, id, colour style)", false, quest.getTitle().toString())));
        } catch (Throwable t) {
            me.langyue.autotranslation.smoke.SmokeTest.check("ftbquests: functional test setup", false, t.toString());
        }
    }


}
