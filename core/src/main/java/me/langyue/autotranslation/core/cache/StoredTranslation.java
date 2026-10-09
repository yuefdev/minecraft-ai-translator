package me.langyue.autotranslation.core.cache;

import me.langyue.autotranslation.core.mask.RestoreResult;

import java.util.List;

/**
 * A validated translation: plain text, or for styled text the runs in translated order.
 */
public record StoredTranslation(String text, List<RestoreResult.Run> runs) {

    public static StoredTranslation plain(String text) {
        return new StoredTranslation(text, List.of());
    }

    public static StoredTranslation from(RestoreResult result) {
        return new StoredTranslation(result.text(), result.runs());
    }

    public boolean hasRuns() {
        return !runs.isEmpty();
    }
}
