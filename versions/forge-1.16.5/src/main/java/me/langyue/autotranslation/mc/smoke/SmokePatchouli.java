package me.langyue.autotranslation.mc.smoke;

import me.langyue.autotranslation.mc.Texts;
import me.langyue.autotranslation.core.smoke.SmokeReport;
import me.langyue.autotranslation.mc.patchouli.PatchouliSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.client.book.page.PageText;
import vazkii.patchouli.common.book.Book;

/**
 * Patchouli part of the start-up check. Kept in its own class so that it is only loaded when the mod is installed.
 */
final class SmokePatchouli {
    private SmokePatchouli() {
    }

    static void run(SmokeReport report) {
        ClassLoader loader = SmokePatchouli.class.getClassLoader();
        report.expectMethod(loader, "vazkii.patchouli.client.book.BookEntry", "translateName", "patchouli: BookEntry.getName hooked");
        report.expectMethod(loader, "vazkii.patchouli.client.book.BookCategory", "translateName", "patchouli: BookCategory.getName hooked");
        report.expectMethod(loader, "vazkii.patchouli.client.book.BookPage", "translateLabel", "patchouli: BookPage.i18n hooked");
        report.expectMethod(loader, "vazkii.patchouli.client.book.text.BookTextParser", "translateBeforeLayout", "patchouli: BookTextParser.parse hooked before layout");
        try {
            final Book book = SmokeFtb.allocate(Book.class);
            SmokeFtb.set(book, "id", new ResourceLocation("smoke", "book"));
            checkParserHook(report, book);
            final PageText page = new PageText();
            page.book = book;
            String before = page.i18n("Iron Age");
            report.check("patchouli: original label shown while translation is pending", before.equals("Iron Age"), before);
            report.waitFor("patchouli: page title translated through BookPage.i18n -> pipeline -> provider",
                    () -> page.i18n("Iron Age").equals("TR| Iron Age"), 600,
                    () -> page.i18n("Iron Age"));
            final String markup = "Smelt the ore in a $(item)furnace$(), then $(l:smoke:book/entry)read more$(/l).$(br)Done.";
            final Component original = Texts.literal(markup);
            PatchouliSupport.translatePageText(book, original);
            report.waitFor("patchouli: page markup commands preserved in the translation",
                    () -> {
                        String text = PatchouliSupport.translatePageText(book, original).getString();
                        return text.startsWith("TR| ") && text.contains("$(item)furnace$()")
                                && text.contains("$(l:smoke:book/entry)read more$(/l)") && text.contains("$(br)");
                    }, 600,
                    () -> PatchouliSupport.translatePageText(book, original).getString());
        } catch (Throwable t) {
            report.check("patchouli: functional test setup", false, t.toString());
        }
    }

    /**
     * The page text hook must actually run, not just be merged into the class: a selector that matches nothing still
     * merges the handler. Runs before anything else in this check translates page text, so the hook can only have been
     * seen if Patchouli's own parse call went through the mixin.
     */
    private static void checkParserHook(SmokeReport report, Book book) {
        try {
            vazkii.patchouli.client.book.text.BookTextParser parser =
                    SmokeFtb.allocate(vazkii.patchouli.client.book.text.BookTextParser.class);
            java.lang.reflect.Field field = vazkii.patchouli.client.book.text.BookTextParser.class.getDeclaredField("book");
            field.setAccessible(true);
            field.set(parser, book);
            boolean before = me.langyue.autotranslation.engine.Engine.get().hooksSeen().contains("patchouli.page-text");
            try {
                parser.parse(Texts.literal("Smoke parser text"));
            } catch (Throwable expected) {
                // the parser is only half set up; only the hook at the start of parse matters here
            }
            boolean after = me.langyue.autotranslation.engine.Engine.get().hooksSeen().contains("patchouli.page-text");
            report.check("patchouli: page text hook runs when Patchouli parses a page", !before && after, "before=" + before + " after=" + after);
        } catch (Throwable t) {
            report.check("patchouli: page text hook runs when Patchouli parses a page", false, t.toString());
        }
    }
}
