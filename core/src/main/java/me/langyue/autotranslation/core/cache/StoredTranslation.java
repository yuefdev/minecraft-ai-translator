package me.langyue.autotranslation.core.cache;

import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.mask.RestoreResult;

import java.util.List;

/**
 * A validated translation: plain text, or for styled text the runs in translated order.
 */
public final class StoredTranslation {
    private final String text;
    private final List<RestoreResult.Run> runs;

    public StoredTranslation(String text, List<RestoreResult.Run> runs) {
        this.text = text;
        this.runs = runs;
    }

    public String text() {
        return text;
    }

    public List<RestoreResult.Run> runs() {
        return runs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredTranslation other = (StoredTranslation) o;
        return java.util.Objects.equals(text, other.text) && java.util.Objects.equals(runs, other.runs);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(text, runs);
    }

    @Override
    public String toString() {
        return "StoredTranslation[" + "text=" + text + ", " + "runs=" + runs + "]";
    }


    public static StoredTranslation plain(String text) {
        return new StoredTranslation(text, Compat.listOf());
    }

    public static StoredTranslation from(RestoreResult result) {
        return new StoredTranslation(result.text(), result.runs());
    }

    public boolean hasRuns() {
        return !runs.isEmpty();
    }
}
