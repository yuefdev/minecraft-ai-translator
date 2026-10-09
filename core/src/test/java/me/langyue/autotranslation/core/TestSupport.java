package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.TranslationProvider;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Shared helpers: a scriptable provider that never touches the network, and service wiring. */
final class TestSupport {
    private TestSupport() {
    }

    interface Script {
        List<String> answer(ProviderRequest request, int callNumber) throws ProviderException;
    }

    /** Provider whose answers are produced by a script; records every request it receives. */
    static final class FakeProvider implements TranslationProvider {
        final List<ProviderRequest> requests = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile String model = "fake-model";
        volatile String id = "fake";
        volatile boolean configured = true;
        private final Script script;

        FakeProvider(Script script) {
            this.script = script;
        }

        /** Translates by prefixing each segment, leaving markers/numbers untouched. */
        static FakeProvider prefixing() {
            return new FakeProvider((request, n) -> {
                List<String> out = new ArrayList<>();
                for (String s : request.segments()) out.add("TR| " + s);
                return out;
            });
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String model() {
            return model;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<String> translate(ProviderRequest request) throws ProviderException {
            requests.add(request);
            int n = calls.incrementAndGet();
            return script.answer(request, n);
        }
    }

    static TranslationService.Settings settings() {
        // very high rate so tests do not wait; short timeouts and retry delays
        return new TranslationService.Settings(true, LanguageProfile.TURKISH, 64, 600_000, 2_000, 2, 1,
                3_000, 24, 60_000L, 5, 60_000L, 30);
    }

    static TranslationService.Builder builder(Path dir, FakeProvider provider) {
        TranslationCache cache = new TranslationCache(dir.resolve("cache.jsonl"), 10_000);
        cache.load();
        CorrectionStore corrections = new CorrectionStore(dir.resolve("corrections.json"));
        corrections.load();
        return TranslationService.builder()
                .settings(settings())
                .provider(() -> provider)
                .cache(cache)
                .corrections(corrections)
                .workers(2);
    }

    static Types.TextRequest plain(String contentId, String... texts) {
        List<Types.Segment> segments = new ArrayList<>();
        for (String t : texts) segments.add(Types.Segment.plain(t));
        return new Types.TextRequest(segments,
                new Types.Content("ftbquests", "quest description", contentId, "Getting Started"),
                MaskProfile.FTB_RICH, "test");
    }

    static Types.Result lookupAndWait(TranslationService service, Types.TextRequest request) throws Exception {
        CompletableFuture<Types.Result> done = new CompletableFuture<>();
        Types.Result first = service.lookup(request, done::complete);
        if (first.state() != Types.State.PENDING) return first;
        return done.get(10, TimeUnit.SECONDS);
    }

    static String text(Types.Result result, int index) {
        var segment = result.segments().get(index);
        return segment.translation() == null ? null : segment.translation().text();
    }

    static Consumer<Object> ignore() {
        return o -> {
        };
    }
}
