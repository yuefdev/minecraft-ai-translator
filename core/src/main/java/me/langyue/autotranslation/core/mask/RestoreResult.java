package me.langyue.autotranslation.core.mask;

import me.langyue.autotranslation.core.util.Compat;
import java.util.List;

/**
 * Outcome of putting the protected elements back into a provider answer.
 *
 * @param ok     the answer passed every check
 * @param reason why the answer was rejected (empty when {@code ok})
 * @param text   restored text (plain mode)
 * @param runs   restored runs in the order they appear in the translation (run mode)
 */
public final class RestoreResult {
    private final boolean ok;
    private final String reason;
    private final String text;
    private final List<Run> runs;

    public RestoreResult(boolean ok, String reason, String text, List<Run> runs) {
        this.ok = ok;
        this.reason = reason;
        this.text = text;
        this.runs = runs;
    }

    public boolean ok() {
        return ok;
    }

    public String reason() {
        return reason;
    }

    public String text() {
        return text;
    }

    public List<Run> runs() {
        return runs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestoreResult other = (RestoreResult) o;
        return ok == other.ok && java.util.Objects.equals(reason, other.reason) && java.util.Objects.equals(text, other.text) && java.util.Objects.equals(runs, other.runs);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(ok, reason, text, runs);
    }

    @Override
    public String toString() {
        return "RestoreResult[" + "ok=" + ok + ", " + "reason=" + reason + ", " + "text=" + text + ", " + "runs=" + runs + "]";
    }


    /** One styled fragment of a translated text. {@code styleIndex} refers to the run in the source. */
    public static final class Run {
        private final int styleIndex;
        private final String text;

        public Run(int styleIndex, String text) {
            this.styleIndex = styleIndex;
            this.text = text;
        }

        public int styleIndex() {
            return styleIndex;
        }

        public String text() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Run other = (Run) o;
            return styleIndex == other.styleIndex && java.util.Objects.equals(text, other.text);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(styleIndex, text);
        }

        @Override
        public String toString() {
            return "Run[" + "styleIndex=" + styleIndex + ", " + "text=" + text + "]";
        }
    }

    public static RestoreResult failure(String reason) {
        return new RestoreResult(false, reason, null, Compat.listOf());
    }

    public static RestoreResult success(String text) {
        return new RestoreResult(true, "", text, Compat.listOf());
    }

    public static RestoreResult success(List<Run> runs) {
        StringBuilder joined = new StringBuilder();
        for (Run run : runs) joined.append(run.text());
        return new RestoreResult(true, "", joined.toString(), Compat.listCopyOf(runs));
    }
}
