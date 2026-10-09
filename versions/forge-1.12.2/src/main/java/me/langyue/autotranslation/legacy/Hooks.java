package me.langyue.autotranslation.legacy;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.engine.Engine;
import net.minecraft.client.resources.I18n;

/**
 * Called from the code that {@link Transformer} patched into the optional mods. Every method returns its argument
 * unchanged unless a finished translation is available, never blocks, and never throws.
 */
public final class Hooks {
    private static final Content BETTERQUESTING = new Content("betterquesting", "quest text", "betterquesting:text", "");
    private static final Content FTBQUESTS = new Content("ftbquests", "quest text", "ftbquests:text", "");
    private static final Content PATCHOULI_TEXT = new Content("patchouli", "book page text", "patchouli:text", "");
    private static final Content PATCHOULI_NAME = new Content("patchouli", "book entry or category title", "patchouli:name", "");
    private static final Content PATCHOULI_LABEL = new Content("patchouli", "book page title", "patchouli:label", "");

    private Hooks() {
    }

    /** BetterQuesting: quest, questline and task names and descriptions (raw text, not language keys). */
    public static String betterQuesting(String text) {
        try {
            Engine engine = Engine.get();
            if (text == null || engine == null || !engine.questsEnabled() || looksLikeKey(text)) return text;
            if (I18n.hasKey(text)) return text; // a language key: BetterQuesting translates it itself
            engine.markHook("betterquesting.text");
            return show(engine, text, engine.translate(text, BETTERQUESTING, MaskProfile.PLAIN));
        } catch (Throwable t) {
            return text;
        }
    }

    /** FTB Quests 1.12: raw quest text before FTB Quests resolves {@code {key}} and {@code &} colour codes. */
    public static String ftbQuests(String text) {
        try {
            Engine engine = Engine.get();
            if (text == null || engine == null || !engine.questsEnabled() || looksLikeKey(text)) return text;
            engine.markHook("ftbquests.text");
            return show(engine, text, engine.translate(text, FTBQUESTS, MaskProfile.FTB_RICH));
        } catch (Throwable t) {
            return text;
        }
    }

    public static String patchouliText(String text) {
        return patchouli(text, PATCHOULI_TEXT, "patchouli.page-text");
    }

    public static String patchouliName(String text) {
        return patchouli(text, PATCHOULI_NAME, "patchouli.names");
    }

    public static String patchouliLabel(String text) {
        return patchouli(text, PATCHOULI_LABEL, "patchouli.labels");
    }

    private static String patchouli(String text, Content content, String hook) {
        try {
            Engine engine = Engine.get();
            if (text == null || engine == null || !engine.booksEnabled() || looksLikeKey(text)) return text;
            engine.markHook(hook);
            return show(engine, text, engine.translate(text, content, MaskProfile.PATCHOULI));
        } catch (Throwable t) {
            return text;
        }
    }

    private static String show(Engine engine, String original, String translated) {
        if (translated == null) return original;
        return engine.showOriginal() ? translated + " §7(" + original.replace('§', '&') + ")" : translated;
    }

    /** "item.foo.name", "betterquesting.gui.name": dotted identifiers without spaces are keys, not prose. */
    private static boolean looksLikeKey(String text) {
        return text.indexOf(' ') < 0 && text.indexOf('.') > 0 && text.indexOf('\n') < 0;
    }
}
