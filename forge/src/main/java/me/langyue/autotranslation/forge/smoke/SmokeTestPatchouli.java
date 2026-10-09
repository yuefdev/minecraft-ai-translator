package me.langyue.autotranslation.forge.smoke;

import me.langyue.autotranslation.tr.TranslationRuntime;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Patchouli part of the start-up check. Kept in its own class so that it is only loaded when the mod is installed (the JVM verifier would otherwise
 * try to load the optional mod's classes while verifying {@link SmokeTest}).
 */
final class SmokeTestPatchouli {
    

    private SmokeTestPatchouli() {
    }

    static void run() {
        me.langyue.autotranslation.smoke.SmokeTest.expectMethod("vazkii.patchouli.client.book.BookEntry", "translateName", "patchouli: BookEntry.getName hooked");
        me.langyue.autotranslation.smoke.SmokeTest.expectMethod("vazkii.patchouli.client.book.BookCategory", "translateName", "patchouli: BookCategory.getName hooked");
        me.langyue.autotranslation.smoke.SmokeTest.expectMethod("vazkii.patchouli.client.book.BookPage", "translateLabel", "patchouli: BookPage.i18n hooked");
        me.langyue.autotranslation.smoke.SmokeTest.expectMethod("vazkii.patchouli.client.book.gui.BookTextRenderer", "translateBeforeLayout", "patchouli: BookTextRenderer hooked before layout");
        try {
            var book = allocateBook();
            var page = new vazkii.patchouli.client.book.page.PageText();
            page.book = book;
            String before = page.i18n("Iron Age");
            me.langyue.autotranslation.smoke.SmokeTest.check("patchouli: original label shown while translation is pending", before.equals("Iron Age"), before);
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("patchouli: page title translated through BookPage.i18n -> pipeline -> provider",
                    () -> page.i18n("Iron Age").equals("TR| Iron Age"), 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("patchouli: page title translated through BookPage.i18n -> pipeline -> provider", false,
                            page.i18n("Iron Age") + " | last error: " + TranslationRuntime.service().stats().lastError())));
            String markup = "Smelt the ore in a $(item)furnace$(), then $(l:smoke:book/entry)read more$(/l).$(br)Done.";
            var original = Component.literal(markup);
            me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport.translatePageText(book, original);
            me.langyue.autotranslation.smoke.SmokeTest.WAITING.add(new me.langyue.autotranslation.smoke.SmokeTest.Pending("patchouli: page markup commands preserved in the translation",
                    () -> {
                        Component translated = me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport.translatePageText(book, original);
                        String text = translated.getString();
                        return text.startsWith("TR| ") && text.contains("$(item)furnace$()")
                                && text.contains("$(l:smoke:book/entry)read more$(/l)") && text.contains("$(br)");
                    }, 600,
                    () -> me.langyue.autotranslation.smoke.SmokeTest.check("patchouli: page markup commands preserved in the translation", false,
                            me.langyue.autotranslation.forge.tr.patchouli.PatchouliSupport.translatePageText(book, original).getString())));
        } catch (Throwable t) {
            me.langyue.autotranslation.smoke.SmokeTest.check("patchouli: functional test setup", false, t.toString());
        }
    }

    /** A Book without running its heavy constructor; only the fields our code reads are filled in. */
    private static vazkii.patchouli.common.book.Book allocateBook() throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        var book = (vazkii.patchouli.common.book.Book) allocate.invoke(unsafe, vazkii.patchouli.common.book.Book.class);
        Field id = vazkii.patchouli.common.book.Book.class.getField("id");
        id.setAccessible(true);
        id.set(book, new net.minecraft.resources.ResourceLocation("smoke", "book"));
        return book;
    }


}
