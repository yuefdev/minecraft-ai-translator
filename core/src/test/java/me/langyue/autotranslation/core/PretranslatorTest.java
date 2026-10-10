package me.langyue.autotranslation.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.EngineSettings;
import me.langyue.autotranslation.engine.LanguageSource;
import me.langyue.autotranslation.engine.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The background translation of the mods' language files, against a fake game platform and a local fake provider. */
class PretranslatorTest {
    private static final String KEY = "sk-pretranslate-test-0123456789";
    private static final String[] WORDS = {"Iron", "Copper", "Golden", "Silver", "Crystal", "Obsidian", "Steel", "Bronze", "Cobalt", "Emerald"};
    private static final String[] THINGS = {"Ingot", "Gear", "Plate", "Dust", "Block"};

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger segments = new AtomicInteger();
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
            return false;
        }
    };

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            String body = readAll(exchange.getRequestBody());
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            JsonArray messages = request.getAsJsonArray("messages");
            String user = messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString();
            JsonArray out = new JsonArray();
            for (com.google.gson.JsonElement segment : JsonParser.parseString(user).getAsJsonObject().getAsJsonArray("segments")) {
                segments.incrementAndGet();
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

    private static String readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @AfterEach
    void stop() {
        if (engine != null) engine.shutdown();
        server.stop(0);
        clientThread.shutdownNow();
    }

    private Engine newEngine(boolean pretranslate) {
        EngineSettings s = new EngineSettings();
        s.endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        s.model = "pretranslate-test";
        s.requestsPerMinute = 6000;
        s.maxRetries = 0;
        s.pretranslateLanguageFiles = pretranslate;
        engine = Engine.start(platform, s);
        engine.setApiKey(Secret.of(KEY));
        return engine;
    }

    private static Map<String, String> entries() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String word : WORDS) {
            for (String thing : THINGS) {
                map.put("item." + word.toLowerCase() + "_" + thing.toLowerCase() + ".name", word + " " + thing);
            }
        }
        return map;
    }

    private final LanguageSource source = target -> entries();

    private String translated(String key, String english) {
        return engine.translateLegacy(key, english, null);
    }

    private boolean allTranslated() {
        for (Map.Entry<String, String> e : entries().entrySet()) {
            String t = translated(e.getKey(), e.getValue());
            if (t == null || !t.startsWith("TR| ")) return false;
        }
        return true;
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(25);
    }

    @Test
    void everyTextIsTranslatedBeforeTheGameAsksForIt() throws Exception {
        newEngine(true).startPretranslation(source);
        waitUntil(this::allTranslated);
        assertTrue(allTranslated(), "all " + entries().size() + " texts must be in the cache without having been shown");
        assertEquals(entries().size(), segments.get(), "each text is sent once");
        assertTrue(requests.get() < entries().size(), "texts are sent in batches, not one request each: " + requests.get());
    }

    @Test
    void anAlreadyTranslatedStartAsksTheProviderForNothing() throws Exception {
        newEngine(true).startPretranslation(source);
        waitUntil(this::allTranslated);
        int before = segments.get();
        engine.shutdown();

        newEngine(true).startPretranslation(source);
        Thread.sleep(600);
        assertTrue(allTranslated());
        assertEquals(before, segments.get(), "the second start finds everything in the cache");
    }

    @Test
    void theSettingTurnsItOff() throws Exception {
        newEngine(false).startPretranslation(source);
        Thread.sleep(800);
        assertEquals(0, requests.get());
        assertNull(translated("item.iron_ingot.name", "Iron Ingot"));
    }

    @Test
    void nothingHappensInTheSourceLanguage() throws Exception {
        language = "en_us";
        newEngine(true).startPretranslation(source);
        Thread.sleep(800);
        assertEquals(0, requests.get());
    }

    @Test
    void anUnreadableSourceIsRetriedAndDoesNotCrash() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        newEngine(true).startPretranslation(target -> {
            attempts.incrementAndGet();
            throw new IOException("resources are not loaded yet");
        });
        waitUntil(() -> attempts.get() >= 1);
        assertEquals(1, attempts.get());
        assertEquals(0, requests.get());
    }
}
