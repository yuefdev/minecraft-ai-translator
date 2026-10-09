package me.langyue.autotranslation.core.cache;

import me.langyue.autotranslation.core.util.Hashing;

/**
 * Identity of one cached translation. Every field that can change the meaning or the wording of the result is
 * part of the key, so changing the text, the language, the content it belongs to, the provider/model, the
 * glossary or the prompt automatically stops the old translation from being used.
 */
public record CacheKey(
        String sourceHash,
        String targetLanguage,
        String contentId,
        String provider,
        String model,
        String glossaryVersion,
        String promptVersion
) {
    public static CacheKey of(String sourceText, String targetLanguage, String contentId, String provider,
                              String model, String glossaryVersion, String promptVersion) {
        return new CacheKey(Hashing.sha256(sourceText), targetLanguage, contentId, provider, model,
                glossaryVersion, promptVersion);
    }

    /** Stable fingerprint of all fields; used as the map key and the on-disk key. */
    public String fingerprint() {
        return Hashing.sha256(sourceHash, targetLanguage, contentId, provider, model, glossaryVersion, promptVersion);
    }
}
