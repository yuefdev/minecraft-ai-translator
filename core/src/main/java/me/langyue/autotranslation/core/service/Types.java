package me.langyue.autotranslation.core.service;

import me.langyue.autotranslation.core.util.Compat;
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
    public static final class Segment {
        private final String text;
        private final List<String> runs;

        public Segment(String text, List<String> runs) {
            this.text = text;
            this.runs = runs;
        }

        public String text() {
            return text;
        }

        public List<String> runs() {
            return runs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Segment other = (Segment) o;
            return java.util.Objects.equals(text, other.text) && java.util.Objects.equals(runs, other.runs);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(text, runs);
        }

        @Override
        public String toString() {
            return "Segment[" + "text=" + text + ", " + "runs=" + runs + "]";
        }

        public static Segment plain(String text) {
            return new Segment(text, null);
        }

        public static Segment ofRuns(List<String> runs) {
            return new Segment(String.join("", runs), Compat.listCopyOf(runs));
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
    public static final class Content {
        private final String sourceMod;
        private final String contentType;
        private final String contentId;
        private final String titleContext;

        public Content(String sourceMod, String contentType, String contentId, String titleContext) {
            this.sourceMod = sourceMod;
            this.contentType = contentType;
            this.contentId = contentId;
            this.titleContext = titleContext;
        }

        public String sourceMod() {
            return sourceMod;
        }

        public String contentType() {
            return contentType;
        }

        public String contentId() {
            return contentId;
        }

        public String titleContext() {
            return titleContext;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Content other = (Content) o;
            return java.util.Objects.equals(sourceMod, other.sourceMod) && java.util.Objects.equals(contentType, other.contentType) && java.util.Objects.equals(contentId, other.contentId) && java.util.Objects.equals(titleContext, other.titleContext);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sourceMod, contentType, contentId, titleContext);
        }

        @Override
        public String toString() {
            return "Content[" + "sourceMod=" + sourceMod + ", " + "contentType=" + contentType + ", " + "contentId=" + contentId + ", " + "titleContext=" + titleContext + "]";
        }
    }

    /** A set of related texts (for example all lines of a quest description). */
    public static final class TextRequest {
        private final List<Segment> segments;
        private final Content content;
        private final MaskProfile profile;
        private final String listenerKey;

        public TextRequest(List<Segment> segments, Content content, MaskProfile profile, String listenerKey) {
            this.segments = segments;
            this.content = content;
            this.profile = profile;
            this.listenerKey = listenerKey;
        }

        public List<Segment> segments() {
            return segments;
        }

        public Content content() {
            return content;
        }

        public MaskProfile profile() {
            return profile;
        }

        public String listenerKey() {
            return listenerKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TextRequest other = (TextRequest) o;
            return java.util.Objects.equals(segments, other.segments) && java.util.Objects.equals(content, other.content) && java.util.Objects.equals(profile, other.profile) && java.util.Objects.equals(listenerKey, other.listenerKey);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(segments, content, profile, listenerKey);
        }

        @Override
        public String toString() {
            return "TextRequest[" + "segments=" + segments + ", " + "content=" + content + ", " + "profile=" + profile + ", " + "listenerKey=" + listenerKey + "]";
        }
    }

    /** Result for one segment. {@code translation == null} means: show the original. */
    public static final class SegmentResult {
        private final Origin origin;
        private final StoredTranslation translation;

        public SegmentResult(Origin origin, StoredTranslation translation) {
            this.origin = origin;
            this.translation = translation;
        }

        public Origin origin() {
            return origin;
        }

        public StoredTranslation translation() {
            return translation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SegmentResult other = (SegmentResult) o;
            return java.util.Objects.equals(origin, other.origin) && java.util.Objects.equals(translation, other.translation);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(origin, translation);
        }

        @Override
        public String toString() {
            return "SegmentResult[" + "origin=" + origin + ", " + "translation=" + translation + "]";
        }

        public static final SegmentResult ORIGINAL = new SegmentResult(Origin.ORIGINAL, null);
    }

    /**
     * @param degraded some segments could not be translated and are shown as the original
     * @param detail   human readable reason for FAILED/degraded states (already redacted)
     */
    public static final class Result {
        private final State state;
        private final List<SegmentResult> segments;
        private final boolean degraded;
        private final String detail;

        public Result(State state, List<SegmentResult> segments, boolean degraded, String detail) {
            this.state = state;
            this.segments = segments;
            this.degraded = degraded;
            this.detail = detail;
        }

        public State state() {
            return state;
        }

        public List<SegmentResult> segments() {
            return segments;
        }

        public boolean degraded() {
            return degraded;
        }

        public String detail() {
            return detail;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result other = (Result) o;
            return java.util.Objects.equals(state, other.state) && java.util.Objects.equals(segments, other.segments) && degraded == other.degraded && java.util.Objects.equals(detail, other.detail);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(state, segments, degraded, detail);
        }

        @Override
        public String toString() {
            return "Result[" + "state=" + state + ", " + "segments=" + segments + ", " + "degraded=" + degraded + ", " + "detail=" + detail + "]";
        }

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
    public static final class Stats {
        private final int queued;
        private final int inFlight;
        private final long translatedSegments;
        private final long failedSegments;
        private final long cacheHits;
        private final long providerCalls;
        private final String lastError;
        private final long lastErrorAtMillis;
        private final long pausedUntilMillis;
        private final int cacheSize;

        public Stats(int queued, int inFlight, long translatedSegments, long failedSegments, long cacheHits, long providerCalls, String lastError, long lastErrorAtMillis, long pausedUntilMillis, int cacheSize) {
            this.queued = queued;
            this.inFlight = inFlight;
            this.translatedSegments = translatedSegments;
            this.failedSegments = failedSegments;
            this.cacheHits = cacheHits;
            this.providerCalls = providerCalls;
            this.lastError = lastError;
            this.lastErrorAtMillis = lastErrorAtMillis;
            this.pausedUntilMillis = pausedUntilMillis;
            this.cacheSize = cacheSize;
        }

        public int queued() {
            return queued;
        }

        public int inFlight() {
            return inFlight;
        }

        public long translatedSegments() {
            return translatedSegments;
        }

        public long failedSegments() {
            return failedSegments;
        }

        public long cacheHits() {
            return cacheHits;
        }

        public long providerCalls() {
            return providerCalls;
        }

        public String lastError() {
            return lastError;
        }

        public long lastErrorAtMillis() {
            return lastErrorAtMillis;
        }

        public long pausedUntilMillis() {
            return pausedUntilMillis;
        }

        public int cacheSize() {
            return cacheSize;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Stats other = (Stats) o;
            return queued == other.queued && inFlight == other.inFlight && translatedSegments == other.translatedSegments && failedSegments == other.failedSegments && cacheHits == other.cacheHits && providerCalls == other.providerCalls && java.util.Objects.equals(lastError, other.lastError) && lastErrorAtMillis == other.lastErrorAtMillis && pausedUntilMillis == other.pausedUntilMillis && cacheSize == other.cacheSize;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(queued, inFlight, translatedSegments, failedSegments, cacheHits, providerCalls, lastError, lastErrorAtMillis, pausedUntilMillis, cacheSize);
        }

        @Override
        public String toString() {
            return "Stats[" + "queued=" + queued + ", " + "inFlight=" + inFlight + ", " + "translatedSegments=" + translatedSegments + ", " + "failedSegments=" + failedSegments + ", " + "cacheHits=" + cacheHits + ", " + "providerCalls=" + providerCalls + ", " + "lastError=" + lastError + ", " + "lastErrorAtMillis=" + lastErrorAtMillis + ", " + "pausedUntilMillis=" + pausedUntilMillis + ", " + "cacheSize=" + cacheSize + "]";
        }
    }
}
