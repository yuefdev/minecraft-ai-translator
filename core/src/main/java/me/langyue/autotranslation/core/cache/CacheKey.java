package me.langyue.autotranslation.core.cache;

import me.langyue.autotranslation.core.util.Hashing;

/**
 * Identity of one cached translation. Every field that can change the meaning or the wording of the result is
 * part of the key, so changing the text, the language, the content it belongs to, the provider/model, the
 * glossary or the prompt automatically stops the old translation from being used.
 */
public final class CacheKey {
    private final String sourceHash;
    private final String targetLanguage;
    private final String contentId;
    private final String provider;
    private final String model;
    private final String glossaryVersion;
    private final String promptVersion;

    public CacheKey(String sourceHash, String targetLanguage, String contentId, String provider, String model, String glossaryVersion, String promptVersion) {
        this.sourceHash = sourceHash;
        this.targetLanguage = targetLanguage;
        this.contentId = contentId;
        this.provider = provider;
        this.model = model;
        this.glossaryVersion = glossaryVersion;
        this.promptVersion = promptVersion;
    }

    public String sourceHash() {
        return sourceHash;
    }

    public String targetLanguage() {
        return targetLanguage;
    }

    public String contentId() {
        return contentId;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public String glossaryVersion() {
        return glossaryVersion;
    }

    public String promptVersion() {
        return promptVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey other = (CacheKey) o;
        return java.util.Objects.equals(sourceHash, other.sourceHash) && java.util.Objects.equals(targetLanguage, other.targetLanguage) && java.util.Objects.equals(contentId, other.contentId) && java.util.Objects.equals(provider, other.provider) && java.util.Objects.equals(model, other.model) && java.util.Objects.equals(glossaryVersion, other.glossaryVersion) && java.util.Objects.equals(promptVersion, other.promptVersion);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sourceHash, targetLanguage, contentId, provider, model, glossaryVersion, promptVersion);
    }

    @Override
    public String toString() {
        return "CacheKey[" + "sourceHash=" + sourceHash + ", " + "targetLanguage=" + targetLanguage + ", " + "contentId=" + contentId + ", " + "provider=" + provider + ", " + "model=" + model + ", " + "glossaryVersion=" + glossaryVersion + ", " + "promptVersion=" + promptVersion + "]";
    }

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
