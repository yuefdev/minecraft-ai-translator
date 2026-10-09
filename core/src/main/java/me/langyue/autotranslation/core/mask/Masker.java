package me.langyue.autotranslation.core.mask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protects everything in a text that must survive translation untouched (placeholders, colour codes, URLs,
 * ids, Patchouli commands, ...) by replacing it with opaque tokens before the text is sent to a provider, and
 * verifies the provider's answer when the tokens are put back.
 *
 * <p>An answer is rejected - and the caller shows the original text instead - if a token is missing, duplicated
 * or invented, if numbers changed, if the line structure changed, or if it looks like chatter rather than a
 * translation.
 */
public final class Masker {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("\\[\\[\\s*P\\s*(\\d+)\\s*]]");
    private static final Pattern RUN_TAG = Pattern.compile("\\[\\[\\s*(/?)\\s*R\\s*(\\d+)\\s*]]");
    private static final Pattern ANY_TOKEN_LIKE = Pattern.compile("\\[\\[\\s*/?\\s*[PR]\\s*\\d+\\s*]]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private static final List<String> PREAMBLES = List.of(
            "here is the translation", "here's the translation", "sure,", "sure!", "certainly", "of course",
            "translation:", "translated text:", "i'm sorry", "i am sorry", "as an ai", "elbette", "tabii",
            "çeviri:", "ceviri:", "işte çeviri", "iste ceviri", "işte türkçe", "üzgünüm"
    );

    private static final String URL = "(?:https?://|www\\.)[^\\s<>\"'`]*[^\\s<>\"'`.,;:!?)\\]]";
    private static final String DOLLAR_COMMAND = "\\$\\([^)\\n]*\\)";
    private static final String BRACES = "\\{[^{}\\n]*}";
    private static final String ESCAPES = "\\\\[&{}n]";
    private static final String PRINTF = "%%|%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[sdfxXoeEgGcbnS]";
    private static final String SECTION_HEX = "§x(?:§[0-9a-fA-F]){6}";
    private static final String SECTION_CODE = "§[0-9a-fk-orA-FK-OR]";
    private static final String AMP_CODE = "&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]";
    private static final String BACKTICKS = "`[^`\\n]+`";
    private static final String COMMAND = "(?<![\\w/:.])/[A-Za-z][A-Za-z0-9_]*(?:[ ]+(?:[@~^][^\\s]*|\\d[^\\s]*|[^\\s]*:[^\\s]+))*";
    private static final String RESOURCE_ID = "(?<![\\w:/.#-])#?[a-z][a-z0-9_.-]*:[a-z0-9_][a-z0-9_./-]*(?<![./-])";

    private final Map<MaskProfile, Pattern> patterns = new ConcurrentHashMap<>();

    public Masker() {
    }

    private Pattern patternFor(MaskProfile profile) {
        return patterns.computeIfAbsent(profile, p -> {
            List<String> parts = new ArrayList<>();
            parts.add(URL);
            if (p.dollarCommands) parts.add(DOLLAR_COMMAND);
            if (p == MaskProfile.FTB_RICH || p == MaskProfile.PATCHOULI) parts.add(ESCAPES);
            parts.add(BRACES);
            parts.add(PRINTF);
            parts.add(SECTION_HEX);
            parts.add(SECTION_CODE);
            if (p.ampersandCodes) parts.add(AMP_CODE);
            parts.add(BACKTICKS);
            parts.add(COMMAND);
            parts.add(RESOURCE_ID);
            return Pattern.compile(String.join("|", parts));
        });
    }

    // ------------------------------------------------------------------------------------------------ masking

    public MaskedText mask(String source, MaskProfile profile) {
        if (source == null) source = "";
        if (ANY_TOKEN_LIKE.matcher(source).find()) return MaskedText.unsafe(source, profile);
        List<String> placeholders = new ArrayList<>();
        String masked = maskInto(source, profile, placeholders);
        return new MaskedText(source, masked, List.copyOf(placeholders), 0, false, profile);
    }

    /**
     * Masks a text that consists of several independently styled runs. Each run is wrapped in
     * {@code [[R<i>]]...[[/R<i>]]} so that the style of every fragment can be restored after translation even
     * if the translation reorders the fragments.
     */
    public MaskedText maskRuns(List<String> runs, MaskProfile profile) {
        StringBuilder original = new StringBuilder();
        for (String run : runs) {
            if (run != null && ANY_TOKEN_LIKE.matcher(run).find()) {
                return MaskedText.unsafe(String.join("", runs), profile);
            }
            original.append(run == null ? "" : run);
        }
        List<String> placeholders = new ArrayList<>();
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            String run = runs.get(i) == null ? "" : runs.get(i);
            masked.append("[[R").append(i).append("]]")
                    .append(maskInto(run, profile, placeholders))
                    .append("[[/R").append(i).append("]]");
        }
        return new MaskedText(original.toString(), masked.toString(), List.copyOf(placeholders), runs.size(), false, profile);
    }

    private String maskInto(String source, MaskProfile profile, List<String> placeholders) {
        Matcher m = patternFor(profile).matcher(source);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            if (m.end() == m.start()) continue;
            out.append(source, last, m.start());
            out.append("[[P").append(placeholders.size()).append("]]");
            placeholders.add(m.group());
            last = m.end();
        }
        out.append(source, last, source.length());
        return out.toString();
    }

    /**
     * Cuts a piece out of an already masked text and renumbers its tokens, so that the piece can be translated
     * and validated on its own (used to split very long texts without ever splitting inside a protected element).
     */
    public MaskedText slice(MaskedText whole, String maskedPiece) {
        List<String> local = new ArrayList<>();
        Map<Integer, Integer> renumber = new java.util.HashMap<>();
        Matcher m = PLACEHOLDER_TOKEN.matcher(maskedPiece);
        StringBuilder masked = new StringBuilder();
        StringBuilder original = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int global = Integer.parseInt(m.group(1));
            int idx = renumber.computeIfAbsent(global, g -> {
                local.add(whole.placeholders().get(g));
                return local.size() - 1;
            });
            String between = maskedPiece.substring(last, m.start());
            masked.append(between).append("[[P").append(idx).append("]]");
            original.append(between).append(whole.placeholders().get(global));
            last = m.end();
        }
        String tail = maskedPiece.substring(last);
        masked.append(tail);
        original.append(tail);
        return new MaskedText(original.toString(), masked.toString(), List.copyOf(local), 0, false, whole.profile());
    }

    /** The text a reader would see: protected elements and run tags removed. Used for language analysis. */
    public String visibleText(MaskedText masked) {
        String text = RUN_TAG.matcher(masked.masked()).replaceAll("");
        return PLACEHOLDER_TOKEN.matcher(text).replaceAll(" ");
    }

    /** True if after removing protected elements no letter remains (nothing to translate). */
    public boolean hasNoTranslatableLetters(MaskedText masked) {
        return !LETTER.matcher(visibleText(masked)).find();
    }

    // ----------------------------------------------------------------------------------------------- restoring

    public RestoreResult restore(MaskedText source, String translated) {
        return restore(source, translated, true);
    }

    /**
     * @param checkCommentary reject answers that start like chatter ("Sure, here is..."). Disabled for text the
     *                        user typed themselves, which is trusted to be a translation.
     */
    public RestoreResult restore(MaskedText source, String translated, boolean checkCommentary) {
        if (source.unsafe()) return RestoreResult.failure("source contains token-like text");
        if (translated == null || translated.isBlank()) return RestoreResult.failure("empty translation");

        String normalized = normalizeTokens(translated);

        String problem = checkPlaceholders(source, normalized);
        if (problem != null) return RestoreResult.failure(problem);

        String visibleSource = stripTokens(source.masked());
        String visibleTarget = stripTokens(normalized);

        if (!digitRuns(visibleSource).equals(digitRuns(visibleTarget))) {
            return RestoreResult.failure("numbers changed");
        }
        if (source.profile() != null && source.profile().forbiddenInVisibleText != null
                && checkCommentary && source.profile().forbiddenInVisibleText.matcher(visibleTarget).find()
                && !source.profile().forbiddenInVisibleText.matcher(visibleSource).find()) {
            return RestoreResult.failure("answer contains markup characters that were not in the source");
        }
        if (count(source.masked(), '\n') != count(normalized, '\n')) {
            return RestoreResult.failure("line structure changed");
        }
        if (visibleSource.strip().length() >= 12) {
            double ratio = (double) visibleTarget.strip().length() / Math.max(1, visibleSource.strip().length());
            if (ratio < 0.2 || ratio > 5.0) return RestoreResult.failure("implausible length");
        }
        String lowerTarget = visibleTarget.strip().toLowerCase(Locale.ROOT);
        String lowerSource = visibleSource.strip().toLowerCase(Locale.ROOT);
        for (String preamble : PREAMBLES) {
            if (checkCommentary && lowerTarget.startsWith(preamble) && !lowerSource.startsWith(preamble)) {
                return RestoreResult.failure("answer starts with commentary");
            }
        }

        if (!source.isRunMode()) {
            String restored = substitute(normalized.strip(), source.placeholders());
            return RestoreResult.success(copyEdgeWhitespace(source.original(), restored));
        }
        return restoreRuns(source, normalized);
    }

    private RestoreResult restoreRuns(MaskedText source, String normalized) {
        List<RestoreResult.Run> runs = new ArrayList<>();
        boolean[] opened = new boolean[source.runCount()];
        boolean[] closed = new boolean[source.runCount()];
        Matcher m = RUN_TAG.matcher(normalized);
        int cursor = 0;
        int current = -1;
        StringBuilder leading = new StringBuilder();
        StringBuilder body = new StringBuilder();
        while (m.find()) {
            String between = normalized.substring(cursor, m.start());
            boolean closing = !m.group(1).isEmpty();
            int index = Integer.parseInt(m.group(2));
            if (index < 0 || index >= source.runCount()) return RestoreResult.failure("unknown run tag");
            if (!closing) {
                if (current != -1) return RestoreResult.failure("nested run tags");
                if (opened[index]) return RestoreResult.failure("duplicated run tag");
                opened[index] = true;
                current = index;
                body.setLength(0);
                if (runs.isEmpty()) leading.append(between);
                else appendToLast(runs, between);
            } else {
                if (current != index) return RestoreResult.failure("unbalanced run tags");
                if (closed[index]) return RestoreResult.failure("duplicated run tag");
                closed[index] = true;
                body.append(between);
                runs.add(new RestoreResult.Run(index, body.toString()));
                current = -1;
            }
            cursor = m.end();
        }
        String tail = normalized.substring(cursor);
        if (current != -1) return RestoreResult.failure("unclosed run tag");
        appendToLast(runs, tail);
        for (int i = 0; i < source.runCount(); i++) {
            if (!opened[i] || !closed[i]) return RestoreResult.failure("run tag missing");
        }
        if (!runs.isEmpty() && !leading.isEmpty()) {
            RestoreResult.Run first = runs.get(0);
            runs.set(0, new RestoreResult.Run(first.styleIndex(), leading + first.text()));
        }
        List<RestoreResult.Run> restored = new ArrayList<>(runs.size());
        for (RestoreResult.Run run : runs) {
            restored.add(new RestoreResult.Run(run.styleIndex(), substitute(run.text(), source.placeholders())));
        }
        return RestoreResult.success(restored);
    }

    private static void appendToLast(List<RestoreResult.Run> runs, String extra) {
        if (extra.isEmpty() || runs.isEmpty()) return;
        RestoreResult.Run last = runs.get(runs.size() - 1);
        runs.set(runs.size() - 1, new RestoreResult.Run(last.styleIndex(), last.text() + extra));
    }

    private String checkPlaceholders(MaskedText source, String normalized) {
        int expected = source.placeholders().size();
        int[] seen = new int[expected];
        Matcher m = PLACEHOLDER_TOKEN.matcher(normalized);
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            if (index >= expected) return "invented placeholder";
            seen[index]++;
        }
        for (int count : seen) {
            if (count == 0) return "placeholder lost";
            if (count > 1) return "placeholder duplicated";
        }
        if (!source.isRunMode() && RUN_TAG.matcher(normalized).find()) return "unexpected run tag";
        return null;
    }

    /** Providers sometimes add spaces inside tokens or change their case; accept those spellings. */
    private static String normalizeTokens(String text) {
        String t = PLACEHOLDER_TOKEN.matcher(text).replaceAll(r -> "[[P" + r.group(1) + "]]");
        return RUN_TAG.matcher(t).replaceAll(r -> "[[" + r.group(1) + "R" + r.group(2) + "]]");
    }

    private static String substitute(String text, List<String> placeholders) {
        Matcher m = PLACEHOLDER_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            out.append(placeholders.get(Integer.parseInt(m.group(1))));
            last = m.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private static String stripTokens(String text) {
        String t = RUN_TAG.matcher(text).replaceAll("");
        return PLACEHOLDER_TOKEN.matcher(t).replaceAll(" ");
    }

    private static List<String> digitRuns(String text) {
        List<String> runs = new ArrayList<>();
        Matcher m = DIGITS.matcher(text);
        while (m.find()) runs.add(m.group());
        String[] array = runs.toArray(new String[0]);
        Arrays.sort(array);
        return Arrays.asList(array);
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) n++;
        return n;
    }

    private static String copyEdgeWhitespace(String original, String translated) {
        int start = 0;
        while (start < original.length() && Character.isWhitespace(original.charAt(start)) && original.charAt(start) != '\n') start++;
        int end = original.length();
        while (end > start && Character.isWhitespace(original.charAt(end - 1)) && original.charAt(end - 1) != '\n') end--;
        return original.substring(0, start) + translated + original.substring(end);
    }
}
