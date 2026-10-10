package me.langyue.autotranslation.core.pack;

import me.langyue.autotranslation.core.mask.MaskedText;

import java.util.List;

/**
 * Decides whether a translation from a pack may be shown for a text. A pack comes from the network, so it is treated
 * like the answer of a provider: whatever the game formats, colours or links in the original must be there in the
 * same number, otherwise the original text is used (and the provider asked) instead.
 */
public final class PackValidator {
    static final int MAX_LENGTH = 4000;

    private PackValidator() {
    }

    public static boolean acceptable(MaskedText source, String translated) {
        if (source == null || translated == null || translated.isEmpty() || translated.length() > MAX_LENGTH) return false;
        for (int i = 0; i < translated.length(); i++) {
            char c = translated.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') return false;
        }
        if (translated.contains("[[P") || translated.contains("[[R") || translated.contains("[[/")) return false;
        String original = source.original();
        if (count(original, "§") != count(translated, "§") || count(original, "%") != count(translated, "%")) return false;
        List<String> placeholders = source.placeholders();
        for (int i = 0; i < placeholders.size(); i++) {
            String placeholder = placeholders.get(i);
            if (placeholder.isEmpty()) continue;
            if (count(translated, placeholder) < occurrences(placeholders, placeholder)) return false;
        }
        return true;
    }

    private static int occurrences(List<String> list, String value) {
        int n = 0;
        for (String s : list) if (s.equals(value)) n++;
        return n;
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) n++;
        return n;
    }
}
