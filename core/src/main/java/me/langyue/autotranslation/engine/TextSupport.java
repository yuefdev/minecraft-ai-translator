package me.langyue.autotranslation.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** String helpers for showing the original text next to a translation. */
public final class TextSupport {
    private static final Pattern PATCHOULI_COMMAND = Pattern.compile("\\$\\([^)]*\\)");
    private static final Pattern FTB_CODE = Pattern.compile("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]|§[0-9a-fk-orA-FK-OR]");
    private static final Pattern PRINTF_TOKEN = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sSdDfFgGeExXoOcCbBhHaA]");

    private TextSupport() {
    }

    /**
     * Escapes accidental printf specifiers introduced by translation (e.g. {@code "%50 daha"} or French
     * {@code "50 % de"}) so {@code I18n.get} / {@code String.format} never throws {@code IllegalFormatException}.
     */
    public static String sanitizePrintf(String translation, String original) {
        if (translation == null || original == null || translation.indexOf('%') < 0) return translation;
        List<String> allowedTokens = new ArrayList<String>();
        Matcher src = PRINTF_TOKEN.matcher(original);
        while (src.find()) {
            allowedTokens.add(src.group());
        }
        int len = translation.length();
        StringBuilder out = new StringBuilder(len + 4);
        Matcher m = PRINTF_TOKEN.matcher(translation);
        for (int i = 0; i < len; i++) {
            char c = translation.charAt(i);
            if (c != '%') {
                out.append(c);
                continue;
            }
            if (i + 1 < len && translation.charAt(i + 1) == '%') {
                out.append("%%");
                i++;
                continue;
            }
            m.region(i, len);
            if (m.lookingAt()) {
                String token = m.group();
                int found = indexOfMatchingToken(allowedTokens, token);
                if (found >= 0) {
                    allowedTokens.remove(found);
                    out.append(token);
                    i = m.end() - 1;
                    continue;
                }
                if (!allowedTokens.isEmpty() && token.matches("%\\d+\\$.*")) {
                    out.append(token);
                    i = m.end() - 1;
                    continue;
                }
                out.append("%%");
                continue;
            }
            String prev = i > 0 ? String.valueOf(translation.charAt(i - 1)) : "";
            String next = translation.substring(i + 1, Math.min(len, i + 3));
            if (!next.isEmpty() && original.contains(prev + "%" + next)) {
                out.append('%');
            } else {
                out.append("%%");
            }
        }
        return out.toString();
    }

    private static int indexOfMatchingToken(List<String> tokens, String token) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(token)) return i;
        }
        return -1;
    }

    /** Appends the original in gray after a language-file translation (the result may later be used as a printf format). */
    public static String appendOriginal(String translation, String original, boolean append) {
        String safe = sanitizePrintf(translation, original);
        if (!append) return safe;
        String escaped = original.replace("%%", "%").replace("%", "%%");
        if (!safe.contains("\n")) {
            return safe + " §7* (" + escaped + ")";
        }
        String[] t = safe.split("\n");
        String[] o = escaped.split("\n");
        if (t.length != o.length) {
            return safe + " §7* (" + escaped + ")";
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
