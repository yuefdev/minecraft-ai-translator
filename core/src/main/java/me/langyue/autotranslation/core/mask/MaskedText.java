package me.langyue.autotranslation.core.mask;

import java.util.List;

/**
 * Result of {@link Masker#mask}: the text that is sent to the provider with every protected element replaced by
 * a token such as {@code [[P3]]}, plus what is needed to put the originals back and verify the answer.
 *
 * @param original     the untouched source text (or the runs joined for display purposes)
 * @param masked       text containing tokens; this is what the provider sees
 * @param placeholders protected substrings, index = token number
 * @param runCount     number of style runs when the text was built from styled runs, otherwise 0
 * @param unsafe       the source already contains token-like text, so it cannot be masked reliably
 */
public record MaskedText(String original, String masked, List<String> placeholders, int runCount, boolean unsafe) {

    public boolean isRunMode() {
        return runCount > 0;
    }

    public static MaskedText unsafe(String original) {
        return new MaskedText(original, original, List.of(), 0, true);
    }
}
