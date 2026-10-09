package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.TestSupport.FakeProvider;
import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.glossary.Glossary;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types;
import me.langyue.autotranslation.core.service.Types.Origin;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static me.langyue.autotranslation.core.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {
    @TempDir
    Path dir;

    private TranslationService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    private static TranslationService.Settings with(TranslationService.Settings s, int queue, int rpm, int timeout, int retries,
                                                    int breaker) {
        return new TranslationService.Settings(s.enabled(), s.target(), queue, rpm, timeout, retries, s.retryBaseDelayMillis(),
                s.maxBatchChars(), s.maxSegmentsPerBatch(), s.failureCooldownMillis(), breaker, s.breakerPauseMillis(),
                s.glossaryTermLimit());
    }

    // ------------------------------------------------------------------------------------------ priority

    @Test
    void priorityIsCorrectionThenExistingTextThenCacheThenNewTranslation() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        TranslationService.Builder b = builder(dir, provider)
                .gameLanguage(() -> GameLanguageIndex.build(Map.of("block.minecraft.furnace", "Furnace"),
                        Map.of("block.minecraft.furnace", "Fırın")));
        var corrections = new me.langyue.autotranslation.core.cache.CorrectionStore(dir.resolve("c2.json"));
        corrections.put("Open the door", "Kapıyı aç", null);
        service = b.corrections(corrections).build();

        Types.TextRequest request = plain("ftbquests:quest:1", "Open the door", "Furnace", "Collect iron ingots");
        Result first = lookupAndWait(service, request);

        assertEquals(State.READY, first.state());
        assertEquals(Origin.CORRECTION, first.segments().get(0).origin());
        assertEquals("Kapıyı aç", text(first, 0));
        assertEquals(Origin.EXISTING, first.segments().get(1).origin());
        assertEquals("Fırın", text(first, 1));
        assertEquals(Origin.PROVIDER, first.segments().get(2).origin());
        assertEquals("TR| Collect iron ingots", text(first, 2));
        assertEquals(1, provider.calls.get(), "only the unresolved text may reach the provider");
        assertEquals(List.of("Collect iron ingots"), provider.requests.get(0).segments());

        Result second = service.lookup(request, null);
        assertEquals(State.READY, second.state());
        assertEquals(Origin.CACHE, second.segments().get(2).origin());
        assertEquals(1, provider.calls.get());
    }

    @Test
    void aNewUserCorrectionBeatsAnExistingCachedTranslation() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        var corrections = new me.langyue.autotranslation.core.cache.CorrectionStore(dir.resolve("c.json"));
        service = builder(dir, provider).corrections(corrections).build();
        Types.TextRequest request = plain("ftbquests:quest:1", "Collect iron ingots");
        assertEquals(Origin.PROVIDER, lookupAndWait(service, request).segments().get(0).origin());
        assertEquals(Origin.CACHE, service.lookup(request, null).segments().get(0).origin());

        corrections.put("Collect iron ingots", "Demir külçesi topla", null);
        Result after = service.lookup(request, null);
        assertEquals(Origin.CORRECTION, after.segments().get(0).origin());
        assertEquals("Demir külçesi topla", text(after, 0));
    }

    // ----------------------------------------------------------------------------------- data integrity

    @Test
    void formattingPlaceholdersAndIdsSurviveTheWholePipeline() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        service = builder(dir, provider).build();
        String source = "&6Collect &r%d {item} (minecraft:iron_ingot) for #forge:ingots\nSee https://example.com/guide?id=7";
        Result r = lookupAndWait(service, plain("ftbquests:quest:ABC123", source));
        assertEquals("TR| " + source, text(r, 0));
        String sent = provider.requests.get(0).segments().get(0);
        assertFalse(sent.contains("minecraft:iron_ingot"), "ids must be masked before they leave the game");
        assertFalse(sent.contains("https://"), "urls must be masked");
        assertFalse(sent.contains("&6"), "colour codes must be masked");
    }

    @Test
    void aProviderThatChangesNumbersIsRejectedAndTheOriginalIsShown() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            List<String> out = new ArrayList<>();
            for (String s : req.segments()) out.add(s.replace("25", "52"));
            return out;
        });
        service = builder(dir, provider).build();
        Types.TextRequest request = plain("ftbquests:quest:1", "Kill 25 zombies to progress");
        Result r = lookupAndWait(service, request);
        assertEquals(State.READY, r.state());
        assertTrue(r.degraded());
        assertNull(r.segments().get(0).translation(), "original must be shown");
        assertEquals(2, provider.calls.get(), "one normal and one strict attempt, then give up");
        assertTrue(provider.requests.get(1).strict());
        assertEquals(0, service.stats().cacheSize(), "rejected answers must never be cached");

        // the failure is remembered, so the same text is not sent again on every frame
        service.lookup(request, null);
        assertEquals(2, provider.calls.get());
    }

    @Test
    void aProviderThatDropsPlaceholdersFallsBackToTheOriginal() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            List<String> out = new ArrayList<>();
            for (String s : req.segments()) out.add(s.replaceAll("\\[\\[P\\d+]]", ""));
            return out;
        });
        service = builder(dir, provider).build();
        Result r = lookupAndWait(service, plain("ftbquests:quest:1", "Collect %d iron ingots"));
        assertTrue(r.degraded());
        assertNull(r.segments().get(0).translation());
    }

    @Test
    void wrongAnswerCountIsRetriedAtMostMaxRetriesTimes() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> List.of("only", "one", "too many"));
        service = builder(dir, provider).build();
        Result r = lookupAndWait(service, plain("ftbquests:quest:1", "Collect iron ingots"));
        assertTrue(r.degraded());
        assertEquals(3, provider.calls.get(), "1 try + 2 retries, never unbounded");
    }

    @Test
    void turkishTextAndNonTextIsNeverSentToTheProvider() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        service = builder(dir, provider).build();
        Result r = service.lookup(plain("ftbquests:quest:1", "Bu görev çok kolay", "%d / %d", "minecraft:stone", "   "), null);
        assertEquals(State.READY, r.state());
        for (var segment : r.segments()) assertEquals(Origin.ORIGINAL, segment.origin());
        assertEquals(0, provider.calls.get());
    }

    @Test
    void styledRunsKeepTheirStylesInTranslatedOrder() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) ->
                List.of("[[R2]]Devam etmek için[[/R2]] [[R1]]buraya[[/R1]] [[R0]]tıkla[[/R0]]"));
        service = builder(dir, provider).build();
        Types.TextRequest request = new Types.TextRequest(
                List.of(Types.Segment.ofRuns(List.of("Click ", "here", " to continue"))),
                new Types.Content("patchouli", "book page", "patchouli:book/entry/0", "Intro"),
                MaskProfile.COMPONENT, "t");
        Result r = lookupAndWait(service, request);
        var runs = r.segments().get(0).translation().runs();
        assertEquals(List.of(2, 1, 0), runs.stream().map(x -> x.styleIndex()).toList());
        assertTrue(provider.requests.get(0).segments().get(0).startsWith("[[R0]]Click"));
        // and from the cache
        Result again = service.lookup(request, null);
        assertEquals(Origin.CACHE, again.segments().get(0).origin());
        assertEquals(3, again.segments().get(0).translation().runs().size());
    }

    @Test
    void longTextIsSplitInPiecesAndReassembledLosslessly() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        service = builder(dir, provider).build();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 160; i++) sb.append("This is sentence number ").append(i).append(" of a very long quest description. ");
        String source = sb.toString().stripTrailing();
        Result r = lookupAndWait(service, plain("ftbquests:quest:1", source));
        assertTrue(provider.calls.get() >= 2, "long text must be split over several calls");
        for (var request : provider.requests) {
            int chars = request.segments().stream().mapToInt(String::length).sum();
            assertTrue(chars <= 3_000 + 50, "batch of " + chars + " characters is too large");
        }
        assertEquals(source, text(r, 0).replace("TR| ", ""));
    }

    @Test
    void providerReceivesParagraphContextTitleAndGlossary() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        Glossary glossary = Glossary.of(Map.of("iron ingots", "demir külçesi", "Chest", "Sandık"));
        var corrections = new me.langyue.autotranslation.core.cache.CorrectionStore(dir.resolve("cc.json"));
        corrections.put("Welcome to the base.", "Üsse hoş geldin.", null);
        corrections.put("Return to the chest.", "Sandığa dön.", null);
        service = builder(dir, provider).glossary(glossary).corrections(corrections).build();
        lookupAndWait(service, plain("ftbquests:quest:9", "Welcome to the base.", "Collect iron ingots.", "Return to the chest."));
        var sent = provider.requests.get(0);
        assertEquals(List.of("Collect iron ingots."), sent.segments());
        assertEquals("Welcome to the base.", sent.context().contextBefore());
        assertEquals("Return to the chest.", sent.context().contextAfter());
        assertEquals("ftbquests", sent.context().sourceMod());
        assertEquals("quest description", sent.context().contentType());
        assertEquals("Getting Started", sent.context().titleContext());
        assertEquals("demir külçesi", sent.context().glossary().get("iron ingots"));
        assertFalse(sent.context().glossary().containsKey("Chest"), "only terms of the translated text are sent");
        assertEquals("tr", sent.target().providerCode());
    }

    // --------------------------------------------------------------------------------------- cache keys

    @Test
    void cachedTranslationsAreInvalidatedByLanguageSourceContentProviderModelAndGlossaryChanges() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        Glossary glossary = new Glossary();
        service = builder(dir, provider).glossary(glossary).build();
        Types.TextRequest base = plain("ftbquests:quest:1", "Collect iron ingots");

        lookupAndWait(service, base);
        assertEquals(1, provider.calls.get());
        assertEquals(Origin.CACHE, service.lookup(base, null).segments().get(0).origin());
        assertEquals(1, provider.calls.get());

        lookupAndWait(service, plain("ftbquests:quest:1", "Collect gold ingots"));
        assertEquals(2, provider.calls.get(), "changed source text");

        lookupAndWait(service, plain("ftbquests:quest:2", "Collect iron ingots"));
        assertEquals(3, provider.calls.get(), "changed content id");

        TranslationService.Settings turkish = service.settings();
        service.updateSettings(turkish.withTarget(LanguageProfile.of("de_de")));
        lookupAndWait(service, base);
        assertEquals(4, provider.calls.get(), "changed target language");
        assertEquals("de", provider.requests.get(3).target().providerCode());
        service.updateSettings(turkish);
        assertEquals(Origin.CACHE, service.lookup(base, null).segments().get(0).origin(), "switching back finds the Turkish entry again");
        assertEquals(4, provider.calls.get());

        provider.model = "another-model";
        lookupAndWait(service, base);
        assertEquals(5, provider.calls.get(), "changed model");

        provider.id = "another-provider";
        lookupAndWait(service, base);
        assertEquals(6, provider.calls.get(), "changed provider");

        glossary.put("iron", "demir");
        lookupAndWait(service, base);
        assertEquals(7, provider.calls.get(), "edited glossary");
    }

    @Test
    void cachedTranslationsKeepWorkingWithoutNetwork() throws Exception {
        AtomicBoolean offline = new AtomicBoolean(false);
        FakeProvider provider = new FakeProvider((req, n) -> {
            if (offline.get()) throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect");
            List<String> out = new ArrayList<>();
            for (String s : req.segments()) out.add("TR| " + s);
            return out;
        });
        service = builder(dir, provider).build();
        Types.TextRequest request = plain("ftbquests:quest:1", "Collect iron ingots");
        lookupAndWait(service, request);
        service.close();

        // new session, still offline: the persisted cache answers without any provider call
        offline.set(true);
        FakeProvider offlineProvider = new FakeProvider((req, n) -> {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect");
        });
        service = builder(dir, offlineProvider).build();
        Result cached = service.lookup(request, null);
        assertEquals(State.READY, cached.state());
        assertEquals("TR| Collect iron ingots", text(cached, 0));
        assertEquals(0, offlineProvider.calls.get());

        // a text that is not cached degrades to the original instead of failing hard
        Result uncached = lookupAndWait(service, plain("ftbquests:quest:1", "Collect gold ingots"));
        assertTrue(uncached.degraded());
        assertNull(uncached.segments().get(0).translation());
    }

    // --------------------------------------------------------------------- merging, queue, rate, retries

    @Test
    void identicalRequestsAreMergedIntoOneProviderCall() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        FakeProvider provider = new FakeProvider((req, n) -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new ProviderException(ProviderException.Kind.INTERRUPTED, "interrupted");
            }
            List<String> out = new ArrayList<>();
            for (String s : req.segments()) out.add("TR| " + s);
            return out;
        });
        service = builder(dir, provider).build();
        List<CompletableFuture<Result>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CompletableFuture<Result> f = new CompletableFuture<>();
            futures.add(f);
            Types.TextRequest base = plain("ftbquests:quest:1", "Collect iron ingots");
            Types.TextRequest r = new Types.TextRequest(base.segments(), base.content(), MaskProfile.FTB_RICH, "listener-" + i);
            assertEquals(State.PENDING, service.lookup(r, f::complete).state());
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
        release.countDown();
        for (CompletableFuture<Result> f : futures) {
            assertEquals("TR| Collect iron ingots", text(f.get(10, TimeUnit.SECONDS), 0));
        }
        assertEquals(1, provider.calls.get());
    }

    @Test
    void sameListenerKeyIsNotNotifiedTwice() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FakeProvider provider = new FakeProvider((req, n) -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new ProviderException(ProviderException.Kind.INTERRUPTED, "interrupted");
            }
            return List.of("TR| x");
        });
        service = builder(dir, provider).build();
        List<Result> notified = new CopyOnWriteArrayList<>();
        Types.TextRequest request = plain("ftbquests:quest:1", "Collect iron ingots");
        for (int i = 0; i < 50; i++) service.lookup(request, notified::add);
        release.countDown();
        long deadline = System.currentTimeMillis() + 5_000;
        while (notified.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(100);
        assertEquals(1, notified.size(), "calling lookup every frame must not pile up callbacks");
    }

    @Test
    void queueIsBoundedAndOverflowIsReportedInsteadOfBlocking() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        FakeProvider provider = new FakeProvider((req, n) -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new ProviderException(ProviderException.Kind.INTERRUPTED, "interrupted");
            }
            List<String> out = new ArrayList<>();
            for (String s : req.segments()) out.add("TR| " + s);
            return out;
        });
        service = builder(dir, provider).settings(with(settings(), 2, 600_000, 20_000, 2, 5)).workers(1).build();
        assertEquals(State.PENDING, service.lookup(plain("q:1", "Collect iron ingots"), null).state());
        assertTrue(started.await(5, TimeUnit.SECONDS), "worker must pick up the first job");
        assertEquals(State.PENDING, service.lookup(plain("q:2", "Collect gold ingots"), null).state());
        assertEquals(State.PENDING, service.lookup(plain("q:3", "Collect copper ingots"), null).state());
        long begin = System.nanoTime();
        Result overflow = service.lookup(plain("q:4", "Collect tin ingots"), null);
        assertTrue((System.nanoTime() - begin) < TimeUnit.MILLISECONDS.toNanos(200), "must not block");
        assertEquals(State.FAILED, overflow.state());
        assertTrue(overflow.detail().contains("queue"));
        assertEquals(2, service.stats().queued());
        release.countDown();
    }

    @Test
    void lookupNeverBlocksWhileTheProviderIsSlow() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                throw new ProviderException(ProviderException.Kind.INTERRUPTED, "interrupted");
            }
            return List.of("TR| x");
        });
        service = builder(dir, provider).build();
        long begin = System.nanoTime();
        Result r = service.lookup(plain("q:1", "Collect iron ingots"), null);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        assertEquals(State.PENDING, r.state());
        assertTrue(millis < 150, "lookup took " + millis + "ms");
        assertNull(r.segments().get(0).translation(), "original is shown while waiting");
    }

    @Test
    void callbacksRunOnTheConfiguredClientThread() throws Exception {
        ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-render-thread"));
        try {
            service = builder(dir, FakeProvider.prefixing()).callbackExecutor(clientThread).build();
            CompletableFuture<String> threadName = new CompletableFuture<>();
            service.lookup(plain("q:1", "Collect iron ingots"), result -> threadName.complete(Thread.currentThread().getName()));
            assertEquals("fake-render-thread", threadName.get(5, TimeUnit.SECONDS));
        } finally {
            clientThread.shutdownNow();
        }
    }

    @Test
    void requestsAreSpacedByTheRateLimit() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000);
        List<Long> sleeps = new CopyOnWriteArrayList<>();
        FakeProvider provider = FakeProvider.prefixing();
        service = builder(dir, provider)
                .settings(with(settings(), 64, 60, 5_000, 2, 5)) // 60 per minute = one per second
                .clock(clock::get)
                .sleeper(ms -> {
                    sleeps.add(ms);
                    clock.addAndGet(ms);
                })
                .workers(1)
                .build();
        lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        lookupAndWait(service, plain("q:2", "Collect gold ingots"));
        lookupAndWait(service, plain("q:3", "Collect copper ingots"));
        assertEquals(List.of(1000L, 1000L), sleeps);
    }

    @Test
    void transientErrorsAreRetriedWithBackoffThenSucceed() throws Exception {
        List<Long> sleeps = new CopyOnWriteArrayList<>();
        FakeProvider provider = new FakeProvider((req, n) -> {
            if (n <= 2) throw new ProviderException(ProviderException.Kind.SERVER, "HTTP 503");
            return List.of("TR| ok");
        });
        service = builder(dir, provider).sleeper(sleeps::add).build();
        Result r = lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        assertEquals(3, provider.calls.get());
        assertFalse(r.degraded());
        assertEquals(2, sleeps.size());
        assertTrue(sleeps.get(1) >= sleeps.get(0), "backoff must not shrink: " + sleeps);
    }

    @Test
    void retriesStopAfterTheConfiguredMaximum() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            throw new ProviderException(ProviderException.Kind.SERVER, "HTTP 500");
        });
        service = builder(dir, provider).sleeper(ms -> {
        }).build();
        Result r = lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        assertTrue(r.degraded());
        assertEquals(3, provider.calls.get(), "maxRetries=2 means exactly three attempts");
        assertTrue(service.stats().lastError().contains("500"));
    }

    @Test
    void authenticationErrorsAreNotRetried() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            throw new ProviderException(ProviderException.Kind.AUTH, "Authentication failed");
        });
        service = builder(dir, provider).build();
        Result r = lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        assertTrue(r.degraded());
        assertEquals(1, provider.calls.get());
    }

    @Test
    void serverRetryAfterIsHonoured() throws Exception {
        List<Long> sleeps = new CopyOnWriteArrayList<>();
        FakeProvider provider = new FakeProvider((req, n) -> {
            if (n == 1) throw new ProviderException(ProviderException.Kind.RATE_LIMIT, "HTTP 429", 5_000);
            return List.of("TR| ok");
        });
        service = builder(dir, provider).sleeper(sleeps::add).build();
        lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        assertEquals(List.of(5_000L), sleeps);
    }

    @Test
    void aHangingProviderIsCutOffByTheTimeout() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                throw new ProviderException(ProviderException.Kind.INTERRUPTED, "interrupted");
            }
            return List.of("never");
        });
        service = builder(dir, provider).settings(with(settings(), 64, 600_000, 150, 0, 5)).build();
        long begin = System.nanoTime();
        Result r = lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        assertTrue(r.degraded());
        assertTrue(millis < 3_000, "took " + millis + "ms");
        assertTrue(service.stats().lastError().contains("No answer"), service.stats().lastError());
    }

    @Test
    void repeatedFailuresPauseTheProviderAndStopFurtherCalls() throws Exception {
        FakeProvider provider = new FakeProvider((req, n) -> {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect");
        });
        service = builder(dir, provider).settings(with(settings(), 64, 600_000, 2_000, 0, 2)).build();
        lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        lookupAndWait(service, plain("q:2", "Collect gold ingots"));
        assertEquals(2, provider.calls.get());
        assertTrue(service.stats().pausedUntilMillis() > System.currentTimeMillis());

        Result paused = service.lookup(plain("q:3", "Collect copper ingots"), null);
        assertEquals(State.FAILED, paused.state());
        assertEquals(2, provider.calls.get(), "no calls while paused");

        service.clearFailures();
        assertEquals(State.PENDING, service.lookup(plain("q:3", "Collect copper ingots"), null).state());
    }

    @Test
    void disabledOrUnconfiguredServiceNeverCallsTheProvider() throws Exception {
        FakeProvider provider = FakeProvider.prefixing();
        service = builder(dir, provider).build();
        service.updateSettings(service.settings().withEnabled(false));
        Result disabled = service.lookup(plain("q:1", "Collect iron ingots"), null);
        assertEquals(State.READY, disabled.state());
        assertNull(disabled.segments().get(0).translation());

        service.updateSettings(service.settings().withEnabled(true));
        provider.configured = false;
        Result unconfigured = service.lookup(plain("q:1", "Collect iron ingots"), null);
        assertEquals(State.FAILED, unconfigured.state());
        assertEquals(0, provider.calls.get());
    }

    // ------------------------------------------------------------------------------ correction checks

    @Test
    void userCorrectionsAreValidatedAgainstPlaceholdersAndNumbers() {
        service = builder(dir, FakeProvider.prefixing()).build();
        assertNull(service.validateCorrection("Collect %d iron", "%d demir topla", MaskProfile.FTB_RICH));
        assertNotNull(service.validateCorrection("Collect %d iron", "demir topla", MaskProfile.FTB_RICH));
        assertNotNull(service.validateCorrection("Kill 25 zombies", "26 zombi öldür", MaskProfile.FTB_RICH));
        assertNull(service.validateCorrection("Give %s to {npc}", "{npc} adlı kişiye %s ver", MaskProfile.FTB_RICH),
                "tokens may be reordered");
        assertNull(service.validateCorrection("Of course not", "Elbette hayır", MaskProfile.PLAIN),
                "a user's own wording is trusted");
    }
}
