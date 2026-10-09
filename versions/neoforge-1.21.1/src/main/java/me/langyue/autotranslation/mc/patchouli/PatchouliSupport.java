package me.langyue.autotranslation.mc.patchouli;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.TextSupport;
import me.langyue.autotranslation.mc.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.common.book.Book;

/**
 * Translation of Patchouli books at the points where text is turned into something drawable.
 *
 * <ul>
 *   <li>Page/landing/category text goes through {@code BookTextRenderer}; it is translated <em>before</em> the
 *       markup is parsed and laid out, so wrapping and page fitting are computed for the Turkish text.</li>
 *   <li>Entry and category names and page titles are translated where they are produced.</li>
 * </ul>
 * Books that use language keys already resolve to the book's own Turkish text before these hooks run; only text
 * that is still English afterwards is translated.
 */
public final class PatchouliSupport {
    private PatchouliSupport() {
    }

    /** Page text for {@code BookTextRenderer}; returns the component unchanged when nothing applies. */
    public static Component translatePageText(Book book, Component text) {
        Engine engine = Engine.get();
        if (engine == null || !engine.booksEnabled() || text == null) return text;
        if (!text.getSiblings().isEmpty() || !Texts.isSimple(text)) return text;
        String markup = text.getString();
        if (markup.trim().isEmpty()) return text;
        engine.markHook("patchouli.page-text");
        String translated = engine.translate(markup, pageContent(book), MaskProfile.PATCHOULI);
        if (translated == null) return text;
        if (engine.showOriginal()) translated = TextSupport.appendOriginalPatchouli(translated, markup);
        return Texts.literal(translated).setStyle(text.getStyle());
    }

    /** Entry/category names. */
    public static MutableComponent translateName(Book book, String kind, String id, MutableComponent shown) {
        Engine engine = Engine.get();
        if (engine == null || !engine.booksEnabled() || shown == null) return null;
        String text = shown.getString();
        if (text.trim().isEmpty()) return null;
        engine.markHook("patchouli.names");
        Content content = new Content(namespace(book), "book " + kind + " title", "patchouli:" + book.id + ":" + kind + ":" + id,
                "book: " + book.id);
        String translated = engine.translate(text, content, MaskProfile.PATCHOULI);
        if (translated == null) return null;
        if (engine.showOriginal()) translated = translated + " (" + text + ")";
        return Texts.literal(translated).setStyle(shown.getStyle());
    }

    /** Page titles and labels ({@code BookPage.i18n}). */
    public static String translateLabel(Book book, String shown) {
        Engine engine = Engine.get();
        if (engine == null || !engine.booksEnabled() || shown == null || shown.trim().isEmpty()) return null;
        engine.markHook("patchouli.labels");
        Content content = new Content(namespace(book), "book page title", "patchouli:" + book.id + ":label", entryName());
        String translated = engine.translate(shown, content, MaskProfile.PATCHOULI);
        if (translated == null) return null;
        return engine.showOriginal() ? translated + " (" + shown + ")" : translated;
    }

    private static Content pageContent(Book book) {
        Screen screen = Minecraft.getInstance().screen;
        String where = "page";
        String context = "book: " + book.id;
        if (screen instanceof GuiBookEntry) {
            GuiBookEntry entryScreen = (GuiBookEntry) screen;
            where = "entry:" + entryScreen.getEntry().getId();
            context = "book entry: " + entryScreen.getEntry().getName().getString();
        } else if (screen instanceof GuiBook) {
            where = screen.getClass().getSimpleName();
        }
        return new Content(namespace(book), "book page text", "patchouli:" + book.id + ":" + where, context);
    }

    private static String entryName() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof GuiBookEntry ? "book entry: " + ((GuiBookEntry) screen).getEntry().getId() : "";
    }

    private static String namespace(Book book) {
        return book.id.getNamespace();
    }

    /** Re-creates the open book screen so that pages are laid out again with the new text. */
    public static void refreshOpenScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GuiBook) {
            minecraft.screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
    }
}
