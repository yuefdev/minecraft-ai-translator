package me.langyue.autotranslation.core.service;

import me.langyue.autotranslation.core.cache.StoredTranslation;
import me.langyue.autotranslation.core.mask.MaskProfile;

import java.util.List;

/** Value types of the translation service API. */
public final class Types {
    private Types() {
    }

    /** Where a translation came from. The order is the priority order. */
    public enum Origin {
        /** Nothing to show but the original text (not translatable, failed validation, or still pending). */
        ORIGINAL,
        /** The user's own correction. */
        CORRECTION,
        /** An official/existing Turkish text that the game or a mod already provides. */
        EXISTING,
        /** A validated automatic translation read from the cache. */
        CACHE,
        /** A new automatic translation produced by the provider. */
        PROVIDER
    }

    public enum State {
        /** Every segment is resolved (some may be shown as the original). */
        READY,
        /** At least one segment is being translated; the original must be shown for now. */
        PENDING,
        /** The request cannot be served (queue full, service disabled, ...). */
        FAILED
    }

    /** One text of a request: plain text, or the runs of a styled component. */
    public record Segment(String text, List<String> runs) {
        public static Segment plain(String text) {
            return new Segment(text, null);
        }

        public static Segment ofRuns(List<String> runs) {
            return new Segment(String.join("", runs), List.copyOf(runs));
        }

        public boolean isRuns() {
            return runs != null;
        }
    }

    /**
     * Identity and context of the content being translated.
     *
     * @param sourceMod    mod id, for example {@code ftbquests}
     * @param contentType  for example {@code quest description}
     * @param contentId    stable id of the object, for example {@code ftbquests:quest:1A2B3C:description}
     * @param titleContext chapter/quest/entry title that helps with meaning
     */
    public record Content(String sourceMod, String contentType, String contentId, String titleContext) {
    }

    /** A set of related texts (for example all lines of a quest description). */
    public record TextRequest(List<Segment> segments, Content content, MaskProfile profile, String listenerKey) {
    }

    /** Result for one segment. {@code translation == null} means: show the original. */
    public record SegmentResult(Origin origin, StoredTranslation translation) {
        public static final SegmentResult ORIGINAL = new SegmentResult(Origin.ORIGINAL, null);
    }

    /**
     * @param degraded some segments could not be translated and are shown as the original
     * @param detail   human readable reason for FAILED/degraded states (already redacted)
     */
    public record Result(State state, List<SegmentResult> segments, boolean degraded, String detail) {
        public static Result pending(int size) {
            return new Result(State.PENDING, java.util.Collections.nCopies(size, SegmentResult.ORIGINAL), false, "");
        }

        public static Result failed(int size, String detail) {
            return new Result(State.FAILED, java.util.Collections.nCopies(size, SegmentResult.ORIGINAL), false, detail);
        }

        public boolean ready() {
            return state == State.READY;
        }
    }

    /** Snapshot of the queue for the settings screen. */
    public record Stats(
            int queued,
            int inFlight,
            long translatedSegments,
            long failedSegments,
            long cacheHits,
            long providerCalls,
            String lastError,
            long lastErrorAtMillis,
            long pausedUntilMillis,
            int cacheSize
    ) {
    }
}
