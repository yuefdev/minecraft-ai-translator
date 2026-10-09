package me.langyue.autotranslation.core.mask;

import java.util.List;

/**
 * Outcome of putting the protected elements back into a provider answer.
 *
 * @param ok     the answer passed every check
 * @param reason why the answer was rejected (empty when {@code ok})
 * @param text   restored text (plain mode)
 * @param runs   restored runs in the order they appear in the translation (run mode)
 */
public record RestoreResult(boolean ok, String reason, String text, List<Run> runs) {

    /** One styled fragment of a translated text. {@code styleIndex} refers to the run in the source. */
    public record Run(int styleIndex, String text) {
    }

    public static RestoreResult failure(String reason) {
        return new RestoreResult(false, reason, null, List.of());
    }

    public static RestoreResult success(String text) {
        return new RestoreResult(true, "", text, List.of());
    }

    public static RestoreResult success(List<Run> runs) {
        StringBuilder joined = new StringBuilder();
        for (Run run : runs) joined.append(run.text());
        return new RestoreResult(true, "", joined.toString(), List.copyOf(runs));
    }
}
