package me.langyue.autotranslation.core.service;

import me.langyue.autotranslation.core.cache.CacheKey;
import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.StoredTranslation;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.glossary.Glossary;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.lang.TextAnalyzer;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.mask.MaskedText;
import me.langyue.autotranslation.core.mask.Masker;
import me.langyue.autotranslation.core.mask.RestoreResult;
import me.langyue.autotranslation.core.provider.PromptBuilder;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.TranslationContext;
import me.langyue.autotranslation.core.provider.TranslationProvider;
import me.langyue.autotranslation.core.service.Types.Origin;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.Segment;
import me.langyue.autotranslation.core.service.Types.SegmentResult;
import me.langyue.autotranslation.core.service.Types.State;
import me.langyue.autotranslation.core.service.Types.Stats;
import me.langyue.autotranslation.core.service.Types.TextRequest;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Redactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Resolves texts in priority order - user correction, existing Turkish text, validated cache, new automatic
 * translation - without ever blocking the caller.
 *
 * <p>{@link #lookup} only touches memory. When a translation has to be produced it is queued (bounded queue,
 * identical requests merged), rate limited, executed with a timeout and a bounded number of retries on worker
 * threads, validated, cached, and then reported through the callback executor (the mod points that at the
 * Minecraft client thread). Until then callers keep showing the original text.
 */
public final class TranslationService implements AutoCloseable {

    public record Settings(
            boolean enabled,
            LanguageProfile target,
            int queueCapacity,
            int requestsPerMinute,
            int callTimeoutMillis,
            int maxRetries,
            long retryBaseDelayMillis,
            int maxBatchChars,
            int maxSegmentsPerBatch,
            long failureCooldownMillis,
            int breakerThreshold,
            long breakerPauseMillis,
            int glossaryTermLimit
    ) {
        public static Settings defaults(LanguageProfile target) {
            return new Settings(true, target, 512, 60, 60_000, 2, 1_500, 3_000, 24, 10 * 60_000L, 5, 60_000L, 30);
        }

        public Settings withEnabled(boolean value) {
            return new Settings(value, target, queueCapacity, requestsPerMinute, callTimeoutMillis, maxRetries,
                    retryBaseDelayMillis, maxBatchChars, maxSegmentsPerBatch, failureCooldownMillis,
                    breakerThreshold, breakerPauseMillis, glossaryTermLimit);
        }

        public Settings withTarget(LanguageProfile value) {
            return new Settings(enabled, value, queueCapacity, requestsPerMinute, callTimeoutMillis, maxRetries,
                    retryBaseDelayMillis, maxBatchChars, maxSegmentsPerBatch, failureCooldownMillis,
                    breakerThreshold, breakerPauseMillis, glossaryTermLimit);
        }
    }

    public static final class Builder {
        private Settings settings;
        private Supplier<TranslationProvider> provider = () -> null;
        private TranslationCache cache;
        private CorrectionStore corrections;
        private Glossary glossary = new Glossary();
        private Supplier<GameLanguageIndex> index = GameLanguageIndex::empty;
        private TextAnalyzer analyzer = new TextAnalyzer();
        private Executor callbackExecutor = Runnable::run;
        private LongSupplier clock = System::currentTimeMillis;
        private Sleeper sleeper = Sleeper.SYSTEM;
        private int workers = 2;

        public Builder settings(Settings value) {
            this.settings = value;
            return this;
        }

        public Builder provider(Supplier<TranslationProvider> value) {
            this.provider = value;
            return this;
        }

        public Builder cache(TranslationCache value) {
            this.cache = value;
            return this;
        }

        public Builder corrections(CorrectionStore value) {
            this.corrections = value;
            return this;
        }

        public Builder glossary(Glossary value) {
            this.glossary = value;
            return this;
        }

        public Builder gameLanguage(Supplier<GameLanguageIndex> value) {
            this.index = value;
            return this;
        }

        public Builder analyzer(TextAnalyzer value) {
            this.analyzer = value;
            return this;
        }

        /** Executor on which completion callbacks run (the mod passes the Minecraft client thread). */
        public Builder callbackExecutor(Executor value) {
            this.callbackExecutor = value;
            return this;
        }

        public Builder clock(LongSupplier value) {
            this.clock = value;
            return this;
        }

        public Builder sleeper(Sleeper value) {
            this.sleeper = value;
            return this;
        }

        public Builder workers(int value) {
            this.workers = Math.max(1, value);
            return this;
        }

        public TranslationService build() {
            if (settings == null || cache == null || corrections == null) {
                throw new IllegalStateException("settings, cache and corrections are required");
            }
            TranslationService service = new TranslationService(this);
            service.startWorkers(workers);
            return service;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ----------------------------------------------------------------------------------------------- state

    private record Prepared(MaskedText masked, boolean translatable, String sourceKey) {
    }

    private static final class Unit {
        int segment;
        MaskedText masked;
        String trailing = "";
        boolean passthrough;
        RestoreResult result;
        String failure = "";
    }

    private static final class Job {
        final String key;
        final TextRequest request;
        final Prepared[] prepared;
        final CacheKey[] keys;
        final SegmentResult[] results;
        final List<Integer> unresolved;
        final Map<String, Consumer<Result>> listeners = new ConcurrentHashMap<>();
        final AtomicInteger anonymousListeners = new AtomicInteger();
        String failureDetail = "";

        Job(String key, TextRequest request, Prepared[] prepared, CacheKey[] keys, SegmentResult[] results,
            List<Integer> unresolved) {
            this.key = key;
            this.request = request;
            this.prepared = prepared;
            this.keys = keys;
            this.results = results;
            this.unresolved = unresolved;
        }

        void addListener(String listenerKey, Consumer<Result> listener) {
            if (listener == null) return;
            String id = listenerKey != null ? listenerKey : "#" + anonymousListeners.incrementAndGet();
            listeners.putIfAbsent(id, listener);
        }
    }

    private static final int MEMO_LIMIT = 8192;

    private volatile Settings settings;
    private volatile TextAnalyzer analyzer;
    private final Supplier<TranslationProvider> providerSupplier;
    private final TranslationCache cache;
    private final CorrectionStore corrections;
    private final Glossary glossary;
    private final Supplier<GameLanguageIndex> index;
    private final Executor callbackExecutor;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final Masker masker = new Masker();
    private final RateLimiter rateLimiter;

    private final Map<String, Prepared> preparedMemo = new ConcurrentHashMap<>();
    private final Map<String, Result> readyMemo = new ConcurrentHashMap<>();
    private final Map<String, Job> inflight = new ConcurrentHashMap<>();
    private final Map<String, Long> failures = new ConcurrentHashMap<>();
    private final LinkedBlockingDeque<Job> queue;
    private final ExecutorService workerPool;
    private final ExecutorService callPool;
    private volatile boolean closed = false;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong translated = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong providerCalls = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long pausedUntil = 0L;
    private volatile String lastError = "";
    private volatile long lastErrorAt = 0L;

    private TranslationService(Builder b) {
        this.settings = b.settings;
        this.analyzer = b.analyzer;
        this.providerSupplier = b.provider;
        this.cache = b.cache;
        this.corrections = b.corrections;
        this.glossary = b.glossary;
        this.index = b.index;
        this.callbackExecutor = b.callbackExecutor;
        this.clock = b.clock;
        this.sleeper = b.sleeper;
        this.rateLimiter = new RateLimiter(b.clock, b.sleeper);
        this.queue = new LinkedBlockingDeque<>(Math.max(1, b.settings.queueCapacity()));
        this.workerPool = Executors.newFixedThreadPool(b.workers, daemonFactory("AutoTranslation-Worker"));
        this.callPool = Executors.newCachedThreadPool(daemonFactory("AutoTranslation-Call"));
    }

    private void startWorkers(int count) {
        for (int i = 0; i < count; i++) {
            workerPool.execute(this::workerLoop);
        }
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    // ----------------------------------------------------------------------------------------------- API

    public Settings settings() {
        return settings;
    }

    public void updateSettings(Settings newSettings) {
        this.settings = newSettings;
        readyMemo.clear();
    }

    public void setAnalyzer(TextAnalyzer newAnalyzer) {
        this.analyzer = newAnalyzer;
        preparedMemo.clear();
        readyMemo.clear();
    }

    /** Forget "recently failed" marks so that failed texts are tried again (the "retry" button). */
    public void clearFailures() {
        failures.clear();
        readyMemo.clear();
        pausedUntil = 0L;
        consecutiveFailures.set(0);
    }

    /** Drops memoised results, for example after the user edited a correction. */
    public void invalidateMemo() {
        readyMemo.clear();
    }

    public Masker masker() {
        return masker;
    }

    public Stats stats() {
        TranslationProvider provider = providerSupplier.get();
        return new Stats(queue.size(), inFlight.get(), translated.get(), failed.get(), cacheHits.get(),
                providerCalls.get(), lastError, lastErrorAt, pausedUntil, cache.size());
    }

    public boolean providerConfigured() {
        TranslationProvider provider = providerSupplier.get();
        return provider != null && provider.isConfigured();
    }

    /**
     * Checks a user-entered translation against its source (placeholders, numbers, line structure).
     * @return null if acceptable, otherwise the reason it was rejected
     */
    public String validateCorrection(String source, String translation, MaskProfile profile) {
        MaskedText masked = masker.mask(source, profile);
        if (masked.unsafe()) return null;
        // build a "model answer" by masking the translation with the same placeholders in the same slots
        MaskedText candidate = masker.mask(translation, profile);
        if (!candidate.placeholders().equals(masked.placeholders())) {
            List<String> a = new ArrayList<>(masked.placeholders());
            List<String> b = new ArrayList<>(candidate.placeholders());
            Collections.sort(a);
            Collections.sort(b);
            if (!a.equals(b)) return "placeholder mismatch";
        }
        RestoreResult r = masker.restore(masked, reorderToSourceTokens(masked, candidate));
        return r.ok() ? null : r.reason();
    }

    private String reorderToSourceTokens(MaskedText source, MaskedText candidate) {
        // map each candidate placeholder to the matching source token index (first unused with equal text)
        List<String> sourcePh = source.placeholders();
        boolean[] used = new boolean[sourcePh.size()];
        String text = candidate.masked();
        for (int i = candidate.placeholders().size() - 1; i >= 0; i--) {
            String ph = candidate.placeholders().get(i);
            int target = -1;
            for (int j = 0; j < sourcePh.size(); j++) {
                if (!used[j] && sourcePh.get(j).equals(ph)) {
                    target = j;
                    break;
                }
            }
            if (target < 0) return text;
            used[target] = true;
            text = text.replace("[[P" + i + "]]", "[[P" + target + "]]");
        }
        return text;
    }

    /**
     * Non-blocking lookup. Returns what is known right now; if translations still have to be produced the state
     * is PENDING and {@code onDone} is called later on the callback executor with the final result.
     *
     * @param onDone may be null
     */
    public Result lookup(TextRequest request, Consumer<Result> onDone) {
        Settings st = settings;
        int n = request.segments().size();
        if (!st.enabled() || n == 0) {
            return new Result(State.READY, Collections.nCopies(n, SegmentResult.ORIGINAL), false, "");
        }
        TranslationProvider provider = providerSupplier.get();
        String providerId = provider == null ? "none" : provider.id();
        String model = provider == null ? "none" : provider.model();
        String glossaryVersion = glossary.version();
        GameLanguageIndex gameIndex = index.get();
        String epoch = st.target().minecraftCode() + '|' + providerId + '|' + model + '|' + glossaryVersion + '|'
                + corrections.revision() + '|' + gameIndex.size();
        StringBuilder memoKey = new StringBuilder(epoch).append('|').append(request.content().contentId())
                .append('|').append(request.profile());
        for (Segment s : request.segments()) memoKey.append('\u0001').append(sourceKey(s));
        String memoKeyText = memoKey.toString();
        Result memo = readyMemo.get(memoKeyText);
        if (memo != null) return memo;

        SegmentResult[] results = new SegmentResult[n];
        Prepared[] prepared = new Prepared[n];
        CacheKey[] keys = new CacheKey[n];
        List<Integer> unresolved = new ArrayList<>();
        boolean degraded = false;
        String detail = "";
        long now = clock.getAsLong();
        String scope = request.content().contentId();

        for (int i = 0; i < n; i++) {
            Segment segment = request.segments().get(i);
            Prepared p = prepare(segment, request.profile(), st.target());
            prepared[i] = p;
            if (!p.translatable()) {
                results[i] = SegmentResult.ORIGINAL;
                continue;
            }
            String correction = corrections.find(segment.text(), scope);
            if (correction != null) {
                results[i] = new SegmentResult(Origin.CORRECTION, StoredTranslation.plain(correction));
                continue;
            }
            if (!segment.isRuns()) {
                String existing = gameIndex.lookup(segment.text());
                if (existing != null) {
                    results[i] = new SegmentResult(Origin.EXISTING, StoredTranslation.plain(existing));
                    continue;
                }
            }
            keys[i] = CacheKey.of(p.sourceKey(), st.target().minecraftCode(), scope, providerId, model,
                    glossaryVersion, PromptBuilder.VERSION);
            StoredTranslation cached = cache.get(keys[i]);
            if (cached != null && cached.hasRuns() == segment.isRuns()) {
                cacheHits.incrementAndGet();
                results[i] = new SegmentResult(Origin.CACHE, cached);
                continue;
            }
            Long failedUntil = failures.get(keys[i].fingerprint());
            if (failedUntil != null && failedUntil > now) {
                results[i] = SegmentResult.ORIGINAL;
                degraded = true;
                detail = lastError;
                continue;
            }
            unresolved.add(i);
        }

        if (unresolved.isEmpty()) {
            Result ready = new Result(State.READY, List.of(results), degraded, detail);
            if (!degraded) remember(memoKeyText, ready);
            return ready;
        }
        if (provider == null || !provider.isConfigured()) {
            return new Result(State.FAILED, fillOriginal(results), false, "provider not configured");
        }
        if (clock.getAsLong() < pausedUntil) {
            return new Result(State.FAILED, fillOriginal(results), false, "paused after repeated failures: " + lastError);
        }

        StringBuilder jobKey = new StringBuilder(scope).append('|').append(request.profile());
        for (int index : unresolved) jobKey.append('|').append(keys[index].fingerprint());
        boolean[] rejected = {false};
        String key = jobKey.toString();
        inflight.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.addListener(request.listenerKey(), onDone);
                return existing;
            }
            Job job = new Job(k, request, prepared, keys, results, unresolved);
            job.addListener(request.listenerKey(), onDone);
            if (!queue.offerLast(job)) {
                rejected[0] = true;
                return null;
            }
            return job;
        });
        if (rejected[0]) {
            return new Result(State.FAILED, fillOriginal(results), false, "translation queue is full");
        }
        return new Result(State.PENDING, fillOriginal(results), false, "");
    }

    private static List<SegmentResult> fillOriginal(SegmentResult[] results) {
        List<SegmentResult> list = new ArrayList<>(results.length);
        for (SegmentResult r : results) list.add(r == null ? SegmentResult.ORIGINAL : r);
        return list;
    }

    private void remember(String key, Result result) {
        if (readyMemo.size() >= MEMO_LIMIT) readyMemo.clear();
        readyMemo.put(key, result);
    }

    private static String sourceKey(Segment s) {
        return s.isRuns() ? "R" + String.join("\u0002", s.runs()) : "T" + s.text();
    }

    private Prepared prepare(Segment segment, MaskProfile profile, LanguageProfile target) {
        String memoKey = profile + "|" + target.minecraftCode() + "|" + sourceKey(segment);
        Prepared cached = preparedMemo.get(memoKey);
        if (cached != null) return cached;
        MaskedText masked = segment.isRuns() ? masker.maskRuns(segment.runs(), profile) : masker.mask(segment.text(), profile);
        boolean translatable = !masked.unsafe()
                && !masker.hasNoTranslatableLetters(masked)
                && analyzer.analyze(masker.visibleText(masked), target) == TextAnalyzer.Decision.TRANSLATE;
        Prepared prepared = new Prepared(masked, translatable, sourceKey(segment));
        if (preparedMemo.size() >= MEMO_LIMIT) preparedMemo.clear();
        preparedMemo.put(memoKey, prepared);
        return prepared;
    }

    // --------------------------------------------------------------------------------------------- workers

    private void workerLoop() {
        while (!closed) {
            Job job;
            try {
                job = queue.pollFirst(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (job == null) continue;
            inFlight.incrementAndGet();
            try {
                runJob(job);
            } catch (InterruptedException e) {
                failUnresolved(job, "interrupted");
                return;
            } catch (Throwable t) {
                CoreLog.error("Unexpected translation failure", t);
                recordError("Internal error: " + Redactor.redact(t));
                failUnresolved(job, lastError);
            } finally {
                inFlight.decrementAndGet();
                finish(job);
            }
        }
    }

    private void runJob(Job job) throws InterruptedException {
        Settings st = settings;
        TranslationProvider provider = providerSupplier.get();
        if (clock.getAsLong() < pausedUntil) {
            failUnresolved(job, "paused after repeated failures");
            return;
        }
        if (provider == null || !provider.isConfigured()) {
            recordError("Translation provider is not configured");
            failUnresolved(job, lastError);
            return;
        }

        List<Unit> units = new ArrayList<>();
        for (int index : job.unresolved) {
            MaskedText masked = job.prepared[index].masked();
            if (masked.isRunMode() || masked.masked().length() <= st.maxBatchChars()) {
                units.add(unit(index, masked, ""));
                continue;
            }
            for (TextChunker.Piece piece : TextChunker.split(masked.masked(), st.maxBatchChars())) {
                units.add(unit(index, masker.slice(masked, piece.text()), piece.trailingWhitespace()));
            }
        }

        List<List<Unit>> batches = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        int chars = 0;
        for (Unit unit : units) {
            if (unit.passthrough) continue;
            int size = unit.masked.masked().length();
            if (!current.isEmpty() && (chars + size > st.maxBatchChars() || current.size() >= st.maxSegmentsPerBatch())) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(unit);
            chars += size;
        }
        if (!current.isEmpty()) batches.add(current);

        try {
            for (List<Unit> batch : batches) {
                translateBatch(job, batch, units, st, provider);
            }
            consecutiveFailures.set(0);
        } catch (ProviderException e) {
            recordError(e.getMessage());
            if (e.kind() != ProviderException.Kind.NOT_CONFIGURED) {
                if (consecutiveFailures.incrementAndGet() >= st.breakerThreshold()) {
                    pausedUntil = clock.getAsLong() + st.breakerPauseMillis();
                    consecutiveFailures.set(0);
                    CoreLog.warn("Translation paused for " + st.breakerPauseMillis() / 1000 + "s after repeated failures");
                }
            }
            job.failureDetail = e.getMessage();
        }

        // assemble per segment
        for (int index : job.unresolved) {
            StringBuilder text = new StringBuilder();
            RestoreResult runResult = null;
            boolean complete = true;
            String reason = job.failureDetail;
            for (Unit unit : units) {
                if (unit.segment != index) continue;
                if (unit.passthrough) {
                    text.append(unit.masked.original()).append(unit.trailing);
                } else if (unit.result == null) {
                    complete = false;
                    if (!unit.failure.isEmpty()) reason = unit.failure;
                    break;
                } else if (unit.masked.isRunMode()) {
                    runResult = unit.result;
                } else {
                    text.append(unit.result.text()).append(unit.trailing);
                }
            }
            if (!complete) {
                markFailed(job, index, reason.isEmpty() ? "translation rejected" : reason);
                continue;
            }
            StoredTranslation stored = runResult != null ? StoredTranslation.from(runResult) : StoredTranslation.plain(text.toString());
            cache.put(job.keys[index], stored);
            job.results[index] = new SegmentResult(Origin.PROVIDER, stored);
            translated.incrementAndGet();
        }
    }

    private Unit unit(int segment, MaskedText masked, String trailing) {
        Unit unit = new Unit();
        unit.segment = segment;
        unit.masked = masked;
        unit.trailing = trailing;
        unit.passthrough = !masked.isRunMode() && masker.hasNoTranslatableLetters(masked);
        return unit;
    }

    private void translateBatch(Job job, List<Unit> batch, List<Unit> allUnits, Settings st, TranslationProvider provider)
            throws ProviderException, InterruptedException {
        List<Unit> pending = new ArrayList<>(batch);
        for (int round = 0; round < 2 && !pending.isEmpty(); round++) {
            List<String> segments = new ArrayList<>();
            StringBuilder visible = new StringBuilder();
            for (Unit unit : pending) {
                segments.add(unit.masked.masked());
                visible.append(masker.visibleText(unit.masked)).append('\n');
            }
            TranslationContext context = contextFor(job, pending, allUnits, st, visible.toString());
            ProviderRequest request = new ProviderRequest(st.target(), segments, context, round > 0);
            List<String> answers = callWithRetries(provider, request, st);
            List<Unit> invalid = new ArrayList<>();
            for (int i = 0; i < pending.size(); i++) {
                Unit unit = pending.get(i);
                RestoreResult restored = masker.restore(unit.masked, answers.get(i));
                if (restored.ok()) {
                    unit.result = restored;
                } else {
                    unit.failure = "answer rejected: " + restored.reason();
                    invalid.add(unit);
                    CoreLog.debug("Rejected translation (" + restored.reason() + ")");
                }
            }
            pending = invalid;
        }
    }

    private TranslationContext contextFor(Job job, List<Unit> batch, List<Unit> allUnits, Settings st, String visible) {
        Types.Content content = job.request.content();
        Unit first = batch.get(0);
        Unit last = batch.get(batch.size() - 1);
        String before = "";
        String after = "";
        int firstSegment = first.segment;
        int lastSegment = last.segment;
        List<Segment> segments = job.request.segments();
        if (firstSegment > 0) before = tail(segments.get(firstSegment - 1).text(), 400);
        if (lastSegment + 1 < segments.size()) after = head(segments.get(lastSegment + 1).text(), 400);
        Map<String, String> terms = glossary.matching(visible, st.glossaryTermLimit());
        return new TranslationContext(content.sourceMod(), content.contentType(), content.titleContext(), before, after, terms);
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    private static String head(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private List<String> callWithRetries(TranslationProvider provider, ProviderRequest request, Settings st)
            throws ProviderException, InterruptedException {
        int attempt = 0;
        while (true) {
            rateLimiter.acquire(st.requestsPerMinute());
            try {
                List<String> answers = callWithTimeout(provider, request, st.callTimeoutMillis());
                providerCalls.incrementAndGet();
                return answers;
            } catch (ProviderException e) {
                providerCalls.incrementAndGet();
                if (!e.retryable() || attempt >= st.maxRetries()) throw e;
                long backoff = st.retryBaseDelayMillis() << Math.min(attempt, 6);
                long delay = Math.min(30_000L, Math.max(e.retryAfterMillis(), backoff));
                CoreLog.debug("Retrying translation in " + delay + "ms after: " + e.getMessage());
                sleeper.sleep(delay);
                attempt++;
            }
        }
    }

    private List<String> callWithTimeout(TranslationProvider provider, ProviderRequest request, int timeoutMillis)
            throws ProviderException, InterruptedException {
        Future<List<String>> future = callPool.submit(() -> provider.translate(request));
        try {
            List<String> answers = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (answers == null || answers.size() != request.segments().size()) {
                throw new ProviderException(ProviderException.Kind.BAD_RESPONSE,
                        "The provider returned a different number of translations than requested");
            }
            return answers;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProviderException(ProviderException.Kind.TIMEOUT,
                    "No answer from the translation service within " + timeoutMillis / 1000 + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ProviderException pe) throw pe;
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "Provider failure: " + Redactor.redact(cause));
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    private void failUnresolved(Job job, String reason) {
        for (int index : job.unresolved) {
            if (job.results[index] == null) markFailed(job, index, reason);
        }
    }

    private void markFailed(Job job, int index, String reason) {
        job.results[index] = SegmentResult.ORIGINAL;
        failures.put(job.keys[index].fingerprint(), clock.getAsLong() + settings.failureCooldownMillis());
        failed.incrementAndGet();
        if (job.failureDetail.isEmpty()) job.failureDetail = reason;
    }

    private void recordError(String message) {
        lastError = Redactor.redact(message == null ? "" : message);
        lastErrorAt = clock.getAsLong();
    }

    private void finish(Job job) {
        boolean degraded = false;
        List<SegmentResult> list = new ArrayList<>(job.results.length);
        for (SegmentResult r : job.results) list.add(r == null ? SegmentResult.ORIGINAL : r);
        for (int index : job.unresolved) {
            if (job.results[index] == null || job.results[index].origin() == Origin.ORIGINAL) degraded = true;
        }
        Result result = new Result(State.READY, List.copyOf(list), degraded, degraded ? job.failureDetail : "");
        List<Consumer<Result>> toNotify = new ArrayList<>();
        inflight.computeIfPresent(job.key, (k, v) -> {
            if (v == job) {
                toNotify.addAll(job.listeners.values());
                return null;
            }
            return v;
        });
        readyMemo.clear();
        for (Consumer<Result> listener : toNotify) {
            try {
                callbackExecutor.execute(() -> {
                    try {
                        listener.accept(result);
                    } catch (RuntimeException e) {
                        CoreLog.warn("Translation listener failed", e);
                    }
                });
            } catch (RuntimeException e) {
                CoreLog.warn("Could not dispatch translation result", e);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        workerPool.shutdownNow();
        callPool.shutdownNow();
        cache.compact();
    }
}
