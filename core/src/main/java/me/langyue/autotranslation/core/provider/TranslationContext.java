package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Compat;
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
public final class TranslationContext {
    private final String sourceMod;
    private final String contentType;
    private final String titleContext;
    private final String contextBefore;
    private final String contextAfter;
    private final Map<String, String> glossary;

    public TranslationContext(String sourceMod, String contentType, String titleContext, String contextBefore, String contextAfter, Map<String, String> glossary) {
        this.sourceMod = sourceMod;
        this.contentType = contentType;
        this.titleContext = titleContext;
        this.contextBefore = contextBefore;
        this.contextAfter = contextAfter;
        this.glossary = glossary;
    }

    public String sourceMod() {
        return sourceMod;
    }

    public String contentType() {
        return contentType;
    }

    public String titleContext() {
        return titleContext;
    }

    public String contextBefore() {
        return contextBefore;
    }

    public String contextAfter() {
        return contextAfter;
    }

    public Map<String, String> glossary() {
        return glossary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranslationContext other = (TranslationContext) o;
        return java.util.Objects.equals(sourceMod, other.sourceMod) && java.util.Objects.equals(contentType, other.contentType) && java.util.Objects.equals(titleContext, other.titleContext) && java.util.Objects.equals(contextBefore, other.contextBefore) && java.util.Objects.equals(contextAfter, other.contextAfter) && java.util.Objects.equals(glossary, other.glossary);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sourceMod, contentType, titleContext, contextBefore, contextAfter, glossary);
    }

    @Override
    public String toString() {
        return "TranslationContext[" + "sourceMod=" + sourceMod + ", " + "contentType=" + contentType + ", " + "titleContext=" + titleContext + ", " + "contextBefore=" + contextBefore + ", " + "contextAfter=" + contextAfter + ", " + "glossary=" + glossary + "]";
    }

    public static TranslationContext none() {
        return new TranslationContext("", "", "", "", "", Compat.mapOf());
    }

    public TranslationContext withNeighbours(String before, String after, Map<String, String> terms) {
        return new TranslationContext(sourceMod, contentType, titleContext, before, after, terms);
    }
}
