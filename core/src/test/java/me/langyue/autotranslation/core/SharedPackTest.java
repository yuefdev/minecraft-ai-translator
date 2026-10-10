package me.langyue.autotranslation.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.mask.Masker;
import me.langyue.autotranslation.core.pack.PackBuilder;
import me.langyue.autotranslation.core.pack.PackValidator;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The shared translation pack: validation, building a pack from a cache, and the engine using it instead of a provider. */
class SharedPackTest {
    private static final String KEY = "sk-sharedpack-test-0123456789";
    private static final String[] WORDS = {"Iron", "Copper", "Golden", "Silver", "Crystal", "Obsidian", "Steel", "Bronze"};
    private static final String[] THINGS = {"Ingot", "Gear", "Plate", "Dust"};

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger segments = new AtomicInteger();
    private final ExecutorService clientThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
    private volatile Path dataDir;
    private volatile Path packSite;
    private Engine engine;

    private final Platform platform = new Platform() {
        @Override
        public Path dataDir() {
            return dataDir;
        }

        @Override
        public String languageCode() {
            return "tr_tr";
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
        packSite = dir.resolve("pack-site");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
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
            respond(exchange, 200, root.toString().getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/packs/", exchange -> {
            Path file = packSite.resolve(exchange.getRequestURI().getPath().substring("/packs/".length())).normalize();
            if (!file.startsWith(packSite) || !Files.isRegularFile(file)) {
                respond(exchange, 404, new byte[0]);
            } else {
                respond(exchange, 200, Files.readAllBytes(file));
            }
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
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

    private Engine newEngine(String name, boolean usePack) {
        if (engine != null) engine.shutdown();
        dataDir = dir.resolve(name);
        EngineSettings s = new EngineSettings();
        s.endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        s.model = "pack-test";
        s.requestsPerMinute = 6000;
        s.maxRetries = 0;
        s.sharedPacks = usePack;
        s.sharedPackUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/packs";
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

    private boolean allTranslated() {
        for (Map.Entry<String, String> e : entries().entrySet()) {
            String t = engine.translateLegacy(e.getKey(), e.getValue(), null);
            if (t == null || !t.startsWith("TR| ")) return false;
        }
        return true;
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(25);
    }

    /** A player translates the texts with a provider and builds a pack from the cache. */
    private void buildPackFromAPlayer() throws Exception {
        newEngine("player", false).startPretranslation(source);
        waitUntil(this::allTranslated);
        assertTrue(allTranslated());
        assertEquals(entries().size(), segments.get());
        engine.shutdown();
        engine = null;
        assertEquals(entries().size(), PackBuilder.build(dataDir.resolve("cache").resolve("translations.jsonl"), "tr_tr", packSite));
    }

    @Test
    void aFreshInstallGetsEverythingFromThePackWithoutAskingTheProvider() throws Exception {
        buildPackFromAPlayer();
        int before = segments.get();

        newEngine("newcomer", true).startPretranslation(source);
        waitUntil(this::allTranslated);

        assertTrue(allTranslated(), "all texts come from the pack");
        assertEquals(before, segments.get(), "the provider was not asked for anything");
    }

    @Test
    void withoutThePackTheProviderIsAsked() throws Exception {
        buildPackFromAPlayer();
        int before = segments.get();

        newEngine("newcomer", false).startPretranslation(source);
        waitUntil(this::allTranslated);

        assertTrue(allTranslated());
        assertEquals(before + entries().size(), segments.get());
    }

    @Test
    void aShardThatDoesNotMatchTheManifestIsIgnored() throws Exception {
        buildPackFromAPlayer();
        Path shardFolder = packSite.resolve("v1").resolve("tr_tr");
        try (DirectoryStream<Path> shards = Files.newDirectoryStream(shardFolder, "shard-*.jsonl.gz")) {
            for (Path shard : shards) Files.write(shard, new byte[]{1, 2, 3});
        }
        int before = segments.get();

        newEngine("newcomer", true).startPretranslation(source);
        waitUntil(this::allTranslated);

        assertTrue(allTranslated(), "the provider translates what the damaged pack cannot");
        assertEquals(before + entries().size(), segments.get());
    }

    @Test
    void anUnreachablePackSiteDoesNotStopTranslation() throws Exception {
        buildPackFromAPlayer();
        server.removeContext("/packs/");
        int before = segments.get();

        newEngine("newcomer", true).startPretranslation(source);
        waitUntil(this::allTranslated);

        assertTrue(allTranslated());
        assertEquals(before + entries().size(), segments.get());
    }

    @Test
    void aPackTranslationMustKeepWhatTheOriginalContains() {
        Masker masker = new Masker();
        assertTrue(PackValidator.acceptable(masker.mask("Deals %s damage to %s", MaskProfile.PLAIN), "%s, %s hedefine hasar verir"));
        assertFalse(PackValidator.acceptable(masker.mask("Deals %s damage to %s", MaskProfile.PLAIN), "%s hasar verir"));
        assertFalse(PackValidator.acceptable(masker.mask("Deals %s damage", MaskProfile.PLAIN), "Hasar verir"));
        assertTrue(PackValidator.acceptable(masker.mask("§6Iron§r Ingot", MaskProfile.PLAIN), "§6Demir§r Külçe"));
        assertFalse(PackValidator.acceptable(masker.mask("§6Iron§r Ingot", MaskProfile.PLAIN), "Demir Külçe"));
        assertFalse(PackValidator.acceptable(masker.mask("Iron Ingot", MaskProfile.PLAIN), "§kDemir Külçe"));
        assertFalse(PackValidator.acceptable(masker.mask("Iron Ingot", MaskProfile.PLAIN), "Demir\u0000Külçe"));
        assertFalse(PackValidator.acceptable(masker.mask("Iron Ingot", MaskProfile.PLAIN), ""));
    }
}
