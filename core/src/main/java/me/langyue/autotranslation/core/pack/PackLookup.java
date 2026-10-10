package me.langyue.autotranslation.core.pack;

/** Ready-made translations from a shared pack, looked up by the hash of the source text. */
public interface PackLookup {
    /**
     * @param sourceHash {@link me.langyue.autotranslation.core.cache.CacheKey#sourceHash()} of the text
     * @return the translation, or null if the pack has none for this language and text
     */
    String find(String targetLanguage, String sourceHash);
}
