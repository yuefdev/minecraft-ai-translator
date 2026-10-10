package me.langyue.autotranslation.core.pack;

import java.util.Map;

/** The entries of one language of a shared pack, in memory. */
public final class SharedPack implements PackLookup {
    private final String language;
    private final Map<String, String> entries;

    public SharedPack(String language, Map<String, String> entries) {
        this.language = language;
        this.entries = entries;
    }

    public String language() {
        return language;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String find(String targetLanguage, String sourceHash) {
        return language.equals(targetLanguage) ? entries.get(sourceHash) : null;
    }
}
