package me.langyue.autotranslation.tr;

import me.langyue.autotranslation.core.cache.StoredTranslation;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.Segment;
import me.langyue.autotranslation.core.service.Types.State;
import me.langyue.autotranslation.core.service.Types.TextRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The small API the mixins call. Every method is non-blocking and cheap enough to be called from rendering code:
 * results are memoised per {@link TranslationRuntime#generation()} so that the same text is never looked up
 * twice for the same state of the translator. A {@code null} result always means "show the original".
 */
public final class TranslationBridge {
    private static final int MEMO_LIMIT = 20_000;
    private static final String NONE = "";

    private static final Map<String, String> TEXT_MEMO = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> LINES_MEMO = new ConcurrentHashMap<>();
    private static volatile long memoGeneration = -1;

    private TranslationBridge() {
    }

    private static void syncMemo() {
        long generation = TranslationRuntime.generation();
        if (generation != memoGeneration) {
            TEXT_MEMO.clear();
            LINES_MEMO.clear();
            memoGeneration = generation;
        }
        if (TEXT_MEMO.size() > MEMO_LIMIT) TEXT_MEMO.clear();
        if (LINES_MEMO.size() > MEMO_LIMIT) LINES_MEMO.clear();
    }

    /**
     * @return the translation, or null if the original must be shown (not ready yet, not needed, disabled, failed)
     */
    public static String translate(String text, Content content, MaskProfile profile) {
        if (text == null || text.isEmpty() || !TranslationRuntime.active()) return null;
        syncMemo();
        String memoKey = content.contentId() + '\u0001' + profile.ordinal() + '\u0001' + text;
        String memo = TEXT_MEMO.get(memoKey);
        if (memo != null) return memo.isEmpty() ? null : memo;

        Result result = TranslationRuntime.service().lookup(
                new TextRequest(List.of(Segment.plain(text)), content, profile, memoKey),
                r -> TranslationRuntime.onTranslationArrived());
        String translated = null;
        if (result.state() == State.READY) {
            StoredTranslation t = result.segments().get(0).translation();
            if (t != null && !t.text().equals(text)) translated = t.text();
        }
        // pending/failed results are remembered as "no translation" until the next generation
        TEXT_MEMO.put(memoKey, translated == null ? NONE : translated);
        return translated;
    }

    /**
     * Translates related lines together (for example every line of a quest description) so the provider sees
     * the whole passage. The returned list has the same size and order; lines that need no translation are
     * returned unchanged. Returns null if nothing is available yet.
     */
    public static List<String> translateLines(List<String> lines, Content content, MaskProfile profile) {
        if (lines == null || lines.isEmpty() || !TranslationRuntime.active()) return null;
        syncMemo();
        StringBuilder key = new StringBuilder(content.contentId()).append('\u0001').append(profile.ordinal());
        for (String line : lines) key.append('\u0001').append(line);
        String memoKey = key.toString();
        List<String> memo = LINES_MEMO.get(memoKey);
        if (memo != null) return memo.isEmpty() ? null : memo;

        List<Segment> segments = new ArrayList<>(lines.size());
        for (String line : lines) segments.add(Segment.plain(line));
        Result result = TranslationRuntime.service().lookup(
                new TextRequest(segments, content, profile, memoKey),
                r -> TranslationRuntime.onTranslationArrived());
        List<String> translated = null;
        if (result.state() == State.READY) {
            boolean any = false;
            List<String> out = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                StoredTranslation t = result.segments().get(i).translation();
                if (t != null && !t.text().equals(lines.get(i))) {
                    out.add(t.text());
                    any = true;
                } else {
                    out.add(lines.get(i));
                }
            }
            if (any) translated = List.copyOf(out);
        }
        LINES_MEMO.put(memoKey, translated == null ? List.of() : translated);
        return translated;
    }

    /**
     * Compatibility entry point for the upstream mixins ({@code TranslatorHelper.translate}). Language-file
     * texts may contain printf placeholders, so the original is escaped when it is appended.
     */
    public static String translateLegacy(String key, String english, boolean appendOriginal, Consumer<String> callback) {
        if (english == null || !TranslationRuntime.genericEnabled()) return null;
        String contentId = key == null || key.equals(english) ? "ui" : "lang:" + key;
        Content content = new Content("game", key == null || key.equals(english) ? "interface text" : "language file", contentId, "");
        String translated = translate(english, content, MaskProfile.PLAIN);
        boolean append = TranslationRuntime.showOriginal();
        if (translated != null) {
            String finalText = TextSupport.appendOriginal(translated, english, append);
            if (callback != null) callback.accept(finalText);
            return finalText;
        }
        if (callback != null) {
            // translate() registered the request; hook the callback to the same completion
            TranslationRuntime.service().lookup(
                    new TextRequest(List.of(Segment.plain(english)), content, MaskProfile.PLAIN, "cb:" + contentId + '\u0001' + english),
                    r -> {
                        StoredTranslation t = r.segments().get(0).translation();
                        if (t != null && !t.text().equals(english)) {
                            callback.accept(TextSupport.appendOriginal(t.text(), english, append));
                        }
                    });
        }
        return null;
    }

    /** Cheap hot-path check used by {@code TranslatorHelper.shouldTranslate}. */
    public static boolean needsTranslation(String text) {
        return TranslationRuntime.genericEnabled() && TranslationRuntime.service().needsTranslation(text, MaskProfile.PLAIN);
    }

    /** True if the provider produced or has cached a translation for this exact text right now. */
    public static boolean isTranslated(Types.Result result) {
        return result != null && result.state() == State.READY;
    }
}
