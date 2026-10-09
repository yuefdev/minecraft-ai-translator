package me.langyue.autotranslation.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.EngineSettings;
import me.langyue.autotranslation.engine.Platform;
import me.langyue.autotranslation.engine.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** The version-independent engine, driven through a fake game platform and a local fake provider. */
class EngineTest {
    private static final String KEY = "sk-engine-test-0123456789";

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicBoolean serverFails = new AtomicBoolean(false);
    private final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
    private volatile String language = "tr_tr";
    private Engine engine;

    private final Platform platform = new Platform() {
        @Override
        public Path dataDir() {
            return dir.resolve("AutoTranslation");
        }

        @Override
        public String languageCode() {
            return language;
        }

        @Override
        public void runOnClientThread(Runnable task) {
            clientThread.execute(task);
        }

        @Override
        public boolean isModLoaded(String modId) {
            return modId.equals("ftbquests");
        }
    };

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (serverFails.get()) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            JsonArray messages = request.getAsJsonArray("messages");
            String user = messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString();
            JsonArray out = new JsonArray();
            for (var segment : JsonParser.parseString(user).getAsJsonObject().getAsJsonArray("segments")) {
                out.add("TR| " + segment.getAsString());
            }
            JsonObject content = new JsonObject();
            content.add("translations", out);
            JsonObject message = new JsonObject();
            message.addProperty("content", content.toString());
            JsonObject choice = new JsonObject();
            choice.add("message", message);
            JsonArray choices = new JsonArray();
            choices.add(choice);
            JsonObject root = new JsonObject();
            root.add("choices", choices);
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        if (engine != null) engine.shutdown();
        server.stop(0);
        clientThread.shutdownNow();
    }

    private EngineSettings settings() {
        EngineSettings s = new EngineSettings();
        s.endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        s.model = "engine-test";
        s.requestsPerMinute = 6000;
        s.maxRetries = 0;
        return s;
    }

    private Engine newEngine() {
        engine = Engine.start(platform, settings());
        engine.setApiKey(Secret.of(KEY));
        return engine;
    }

    private static Content content(String id) {
        return new Content("ftbquests", "quest title", id, "Getting Started");
    }

    private String awaitTranslation(Supplier<String> lookup) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        String result;
        while ((result = lookup.get()) == null && System.currentTimeMillis() < deadline) Thread.sleep(20);
        return result;
    }

    @Test
    void textIsShownInEnglishFirstThenTranslatedWithoutBlocking() throws Exception {
        Engine e = newEngine();
        long generation = e.generation();
        long begin = System.nanoTime();
        assertNull(e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin) < 200, "first call must not wait for the network");
        String translated = awaitTranslation(() -> e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        assertEquals("TR| Collect iron ingots", translated);
        assertTrue(e.generation() > generation, "displays are told that something changed");
        int sent = requests.get();
        for (int i = 0; i < 50; i++) e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH);
        assertEquals(sent, requests.get(), "repeated frames must not create requests");
    }

    @Test
    void sourceLanguageAndDisabledStateNeverTranslate() throws Exception {
        Engine e = newEngine();
        language = "en_us";
        assertNull(e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        language = "tr_tr";
        e.settings().enabled = false;
        e.applyConfig();
        assertNull(e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        Thread.sleep(200);
        assertEquals(0, requests.get());
    }

    @Test
    void changingTheGameLanguageIsNoticedByTick() throws Exception {
        Engine e = newEngine();
        e.tick(); // remembers the current language
        long before = e.generation();
        e.tick();
        assertEquals(before, e.generation(), "no change, no rebuild");
        language = "en_us";
        e.tick();
        assertTrue(e.generation() > before, "a language change makes displays rebuild");
        assertNull(e.translate("Collect iron ingots", content("q:9"), MaskProfile.FTB_RICH), "source language: nothing to translate");
    }

    @Test
    void descriptionLinesKeepTheirCountAndPageBreaks() throws Exception {
        Engine e = newEngine();
        List<String> lines = List.of("Mine some iron ore.", "{@pagebreak}", "Bring 25 ingots back.");
        List<String> result = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (result == null && System.currentTimeMillis() < deadline) {
            result = e.translateLines(lines, content("q:2"), MaskProfile.FTB_RICH);
            Thread.sleep(20);
        }
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("TR| Mine some iron ore.", result.get(0));
        assertEquals("{@pagebreak}", result.get(1));
        assertEquals("TR| Bring 25 ingots back.", result.get(2));
    }

    @Test
    void languageFileTextEscapesTheOriginalAndCallsBackOnTheClientThread() throws Exception {
        EngineSettings s = settings();
        s.showOriginal = true;
        engine = Engine.start(platform, s);
        engine.setApiKey(Secret.of(KEY));
        java.util.concurrent.CompletableFuture<String> done = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<String> thread = new java.util.concurrent.CompletableFuture<>();
        engine.translateLegacy("item.test.name", "Gets 50% more %s", text -> {
            thread.complete(Thread.currentThread().getName());
            done.complete(text);
        });
        String text = done.get(10, TimeUnit.SECONDS);
        assertEquals("fake-client-thread", thread.get());
        assertTrue(text.startsWith("TR| Gets 50% more %s"), text);
        assertTrue(text.contains("§7* (Gets 50%% more %%s)"), "appended original must not add printf placeholders: " + text);
    }

    @Test
    void apiKeyLivesOnlyInTheSecretStoreNeverInSettingsOrCache() throws Exception {
        Engine e = newEngine();
        awaitTranslation(() -> e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        new SettingsStore(dir.resolve("settings.json")).save(e.settings());
        Thread.sleep(300); // background writes
        try (var files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString();
                String content = Files.readString(file);
                if (name.equals("api-key.txt")) {
                    assertEquals(KEY, content);
                } else {
                    assertFalse(content.contains(KEY), name + " must not contain the API key");
                }
            }
        }
    }

    @Test
    void cachedTranslationsSurviveARestartAndWorkOffline() throws Exception {
        Engine e = newEngine();
        assertEquals("TR| Collect iron ingots",
                awaitTranslation(() -> e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH)));
        e.shutdown();

        serverFails.set(true);
        int before = requests.get();
        Engine restarted = newEngine();
        assertEquals("TR| Collect iron ingots", restarted.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        assertEquals(before, requests.get(), "a cached translation needs no network access");
    }

    @Test
    void failedProviderLeavesTheOriginalAndDoesNotRetryForever() throws Exception {
        serverFails.set(true);
        Engine e = newEngine();
        for (int i = 0; i < 40; i++) {
            assertNull(e.translate("Collect iron ingots", content("q:9"), MaskProfile.FTB_RICH));
            Thread.sleep(25);
        }
        assertTrue(requests.get() <= 3, "requests were " + requests.get());
        assertTrue(e.service().stats().failedSegments() >= 1);
    }

    @Test
    void screensAreRefreshedOnceTranslationsArriveAndAtMostEvery400ms() throws Exception {
        Engine e = newEngine();
        AtomicInteger refreshes = new AtomicInteger();
        e.registerRefresher(refreshes::incrementAndGet);
        awaitTranslation(() -> e.translate("Collect iron ingots", content("q:1"), MaskProfile.FTB_RICH));
        Thread.sleep(450);
        e.tick();
        assertEquals(1, refreshes.get());
        e.tick();
        e.tick();
        assertEquals(1, refreshes.get(), "no further refresh without new arrivals");
    }

    @Test
    void settingsRoundTripThroughTheirFile() {
        SettingsStore store = new SettingsStore(dir.resolve("settings.json"));
        EngineSettings s = store.load();
        assertTrue(s.enabled);
        s.model = "other";
        s.wordBlacklist.add("Foo");
        store.save(s);
        EngineSettings again = store.load();
        assertEquals("other", again.model);
        assertEquals(List.of("Foo"), again.wordBlacklist);
    }

    @Test
    void optionalModDetectionComesFromThePlatform() {
        Engine e = newEngine();
        assertTrue(e.isModLoaded("ftbquests"));
        assertFalse(e.isModLoaded("patchouli"));
    }
}
