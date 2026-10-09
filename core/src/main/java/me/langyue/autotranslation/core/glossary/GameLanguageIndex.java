package me.langyue.autotranslation.core.glossary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds "English text -> existing Turkish text" from the language files that are already loaded in the game
 * (en_us and tr_tr). It is used in two ways: as glossary hints, and to resolve quest text that is just the
 * English name of something the game already has an official Turkish name for.
 */
public final class GameLanguageIndex {

    private static final Set<String> NAME_PREFIXES = Set.of(
            "item.", "block.", "entity.", "effect.", "enchantment.", "biome.", "attribute.", "itemGroup.",
            "advancements.", "stat.", "gui.", "container."
    );

    private final Map<String, String> byEnglish;

    private GameLanguageIndex(Map<String, String> byEnglish) {
        this.byEnglish = byEnglish;
    }

    public static GameLanguageIndex empty() {
        return new GameLanguageIndex(Map.of());
    }

    /**
     * @param english all en_us entries (key -> value)
     * @param turkish all tr_tr entries (key -> value)
     */
    public static GameLanguageIndex build(Map<String, String> english, Map<String, String> turkish) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> conflicting = new HashSet<>();
        for (Map.Entry<String, String> e : english.entrySet()) {
            String key = e.getKey();
            String en = e.getValue();
            String tr = turkish.get(key);
            if (tr == null || en == null || en.isBlank() || tr.isBlank()) continue;
            if (en.equals(tr)) continue;
            if (en.indexOf('%') >= 0 || en.indexOf('\n') >= 0 || en.length() > 48 || tr.length() > 64) continue;
            if (!isNameKey(key)) continue;
            String previous = result.putIfAbsent(en, tr);
            if (previous != null && !previous.equals(tr)) conflicting.add(en);
        }
        conflicting.forEach(result::remove);
        return new GameLanguageIndex(new HashMap<>(result));
    }

    public static boolean isNameKey(String key) {
        for (String prefix : NAME_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Exact (case-sensitive, trimmed) English text -> official Turkish text, if unambiguous. */
    public String lookup(String english) {
        if (english == null) return null;
        return byEnglish.get(english.strip());
    }

    public Map<String, String> asMap() {
        return byEnglish;
    }

    public int size() {
        return byEnglish.size();
    }
}
