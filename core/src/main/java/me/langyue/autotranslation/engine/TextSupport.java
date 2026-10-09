package me.langyue.autotranslation.engine;

import java.util.regex.Pattern;

/** String helpers for showing the original text next to a translation. */
public final class TextSupport {
    private static final Pattern PATCHOULI_COMMAND = Pattern.compile("\\$\\([^)]*\\)");
    private static final Pattern FTB_CODE = Pattern.compile("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]|§[0-9a-fk-orA-FK-OR]");

    private TextSupport() {
    }

    /** Appends the original in gray after a language-file translation (the result may later be used as a printf format). */
    public static String appendOriginal(String translation, String original, boolean append) {
        if (!append) return translation;
        String escaped = original.replace("%%", "%").replace("%", "%%");
        if (!translation.contains("\n")) {
            return translation + " §7* (" + escaped + ")";
        }
        String[] t = translation.split("\n");
        String[] o = escaped.split("\n");
        if (t.length != o.length) {
            return translation + " §7* (" + escaped + ")";
        }
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < t.length; i++) {
            r.append(t[i]).append(" §7* (").append(o[i]).append(')');
            if (i < t.length - 1) r.append('\n');
        }
        return r.toString();
    }

    /** Readable form of an FTB rich text string without colour codes. */
    public static String plainFtb(String raw) {
        return FTB_CODE.matcher(raw.replace("\\n", " ")).replaceAll("").trim();
    }

    /** Patchouli markup with the original appended in gray after the translated text. */
    public static String appendOriginalPatchouli(String translated, String original) {
        String plain = PATCHOULI_COMMAND.matcher(original).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) return translated;
        return translated + "$(br2)$(#888)(" + plain + ")$()";
    }
}
