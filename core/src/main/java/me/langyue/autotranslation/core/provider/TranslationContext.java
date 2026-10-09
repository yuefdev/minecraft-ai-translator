package me.langyue.autotranslation.core.provider;

import java.util.Map;

/**
 * What the provider is told about the text besides the text itself, so it can pick the right sense of a word.
 *
 * @param sourceMod    mod the text comes from (for example "ftbquests")
 * @param contentType  kind of content (for example "quest description", "book page")
 * @param titleContext title of the chapter/quest/entry the text belongs to
 * @param contextBefore paragraph preceding the segments (context only, never translated)
 * @param contextAfter paragraph following the segments (context only, never translated)
 * @param glossary     terms that must be translated exactly as given
 */
public record TranslationContext(
        String sourceMod,
        String contentType,
        String titleContext,
        String contextBefore,
        String contextAfter,
        Map<String, String> glossary
) {
    public static TranslationContext none() {
        return new TranslationContext("", "", "", "", "", Map.of());
    }

    public TranslationContext withNeighbours(String before, String after, Map<String, String> terms) {
        return new TranslationContext(sourceMod, contentType, titleContext, before, after, terms);
    }
}
