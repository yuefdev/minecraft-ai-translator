package me.langyue.autotranslation.core.mask;

import me.langyue.autotranslation.core.util.Compat;
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
 * @param profile      syntax the text was masked with; decides which characters may not appear in a translation
 */
public final class MaskedText {
    private final String original;
    private final String masked;
    private final List<String> placeholders;
    private final int runCount;
    private final boolean unsafe;
    private final MaskProfile profile;

    public MaskedText(String original, String masked, List<String> placeholders, int runCount, boolean unsafe, MaskProfile profile) {
        this.original = original;
        this.masked = masked;
        this.placeholders = placeholders;
        this.runCount = runCount;
        this.unsafe = unsafe;
        this.profile = profile;
    }

    public String original() {
        return original;
    }

    public String masked() {
        return masked;
    }

    public List<String> placeholders() {
        return placeholders;
    }

    public int runCount() {
        return runCount;
    }

    public boolean unsafe() {
        return unsafe;
    }

    public MaskProfile profile() {
        return profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaskedText other = (MaskedText) o;
        return java.util.Objects.equals(original, other.original) && java.util.Objects.equals(masked, other.masked) && java.util.Objects.equals(placeholders, other.placeholders) && runCount == other.runCount && unsafe == other.unsafe && java.util.Objects.equals(profile, other.profile);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(original, masked, placeholders, runCount, unsafe, profile);
    }

    @Override
    public String toString() {
        return "MaskedText[" + "original=" + original + ", " + "masked=" + masked + ", " + "placeholders=" + placeholders + ", " + "runCount=" + runCount + ", " + "unsafe=" + unsafe + ", " + "profile=" + profile + "]";
    }


    public boolean isRunMode() {
        return runCount > 0;
    }

    public static MaskedText unsafe(String original, MaskProfile profile) {
        return new MaskedText(original, original, Compat.listOf(), 0, true, profile);
    }
}
