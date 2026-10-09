package me.langyue.autotranslation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.provider.GoogleFreeProvider;
import me.langyue.autotranslation.core.provider.OpenAiCompatibleProvider;
import me.langyue.autotranslation.core.provider.OpenAiSettings;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderPreset;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.ProviderSelection;
import me.langyue.autotranslation.core.provider.TranslationContext;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types.Result;
import me.langyue.autotranslation.core.service.Types.State;
import me.langyue.autotranslation.core.util.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static me.langyue.autotranslation.core.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Free services: choosing one from the key, the keyless machine translation and free-tier quota answers. */
class FreeProvidersTest {
    @TempDir
    Path dir;

    private HttpServer server;
    private TranslationService service;

    @AfterEach
    void stop() {
        if (service != null) service.close();
        if (server != null) server.stop(0);
    }

    private static final TranslationContext CONTEXT = new TranslationContext("test", "test", "", "", "", Map.of());

    private static ProviderRequest request(String... segments) {
        return new ProviderRequest(LanguageProfile.TURKISH, List.of(segments), CONTEXT, false);
    }

    // ------------------------------------------------------------------------------------------ selection

    @Test
    void withoutAKeyTheKeylessMachineTranslationIsUsed() {
        ProviderSelection s = ProviderSelection.resolve("auto", Secret.empty(), "https://api.openai.com/v1", "gpt-4o-mini", 60);
        assertEquals(ProviderPreset.GOOGLE_FREE, s.presetId());
        assertTrue(s.usesKeylessMachineTranslation());
    }

    @Test
    void theKeyPicksItsService() {
        assertEquals(ProviderPreset.GEMINI, ProviderSelection.resolve("auto", Secret.of("AIzaSyExampleExampleExample123"), "", "", 60).presetId());
        assertEquals(ProviderPreset.GROQ, ProviderSelection.resolve("auto", Secret.of("gsk_exampleexampleexample"), "", "", 60).presetId());
        assertEquals(ProviderPreset.OPENROUTER, ProviderSelection.resolve("auto", Secret.of("sk-or-v1-exampleexample"), "", "", 60).presetId());
        ProviderSelection openAi = ProviderSelection.resolve("auto", Secret.of("sk-proj-exampleexample"), "https://api.openai.com/v1", "gpt-4o-mini", 60);
        assertEquals(ProviderPreset.CUSTOM, openAi.presetId(), "an unknown key keeps the custom address (OpenAI by default)");
        assertEquals("https://api.openai.com/v1", openAi.baseUrl());
        assertEquals("gpt-4o-mini", openAi.model());
    }

    @Test
    void aLocalServerWithoutAKeyIsKept() {
        ProviderSelection s = ProviderSelection.resolve("auto", Secret.empty(), "http://localhost:11434/v1", "qwen2.5:7b", 60);
        assertEquals(ProviderPreset.CUSTOM, s.presetId());
        assertEquals("http://localhost:11434/v1", s.baseUrl());
    }

    @Test
    void aPresetBringsItsOwnAddressModelAndFreeTierLimits() {
        ProviderPreset gemini = ProviderPreset.byId(ProviderPreset.GEMINI);
        ProviderSelection s = ProviderSelection.resolve("gemini", Secret.of("AIzaSyExampleExampleExample123"), "https://api.openai.com/v1", "gpt-4o-mini", 60);
        assertEquals(gemini.baseUrl(), s.baseUrl());
        assertEquals(gemini.model(), s.model());
        assertEquals(Math.min(60, gemini.requestsPerMinute()), s.requestsPerMinute(), "never above the free tier's limit");
        assertEquals(2, ProviderSelection.resolve("gemini", Secret.empty(), "", "", 2).requestsPerMinute(), "a lower own limit wins");
        ProviderSelection custom = ProviderSelection.resolve("custom", Secret.of("AIzaSyExampleExampleExample123"), "https://example.org/v1", "my-model", 60);
        assertEquals("https://example.org/v1", custom.baseUrl(), "custom ignores what the key looks like");
        assertEquals("my-model", custom.model());
    }

    @Test
    void everyPresetIsComplete() {
        for (ProviderPreset p : ProviderPreset.all()) {
            assertTrue(p.baseUrl().startsWith("https://") || p.baseUrl().startsWith("http://localhost"), p.id());
            assertFalse(p.model().isBlank(), p.id());
            assertTrue(p.requestsPerMinute() > 0 && p.maxBatchChars() >= 500 && p.maxSegmentsPerBatch() >= 4, p.id());
            if (p.keyRequired()) assertTrue(p.keyUrl().startsWith("https://"), p.id() + " tells where to get a key");
        }
        assertEquals(List.of("auto", "google-free", "gemini", "groq", "mistral", "openrouter", "ollama", "custom"), ProviderPreset.ids());
    }

    // ------------------------------------------------------------------------- keyless machine translation

    /** Answers like translate_a/single?client=gtx: one sentence entry per line, "TR " in front of every line. */
    private GoogleFreeProvider googleServer(Function<String, String> translate, List<Map<String, String>> calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate_a/single", exchange -> {
            Map<String, String> query = new HashMap<>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                int eq = pair.indexOf('=');
                query.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
            calls.add(query);
            String answer = translate.apply(query.get("q"));
            if (answer == null) {
                exchange.sendResponseHeaders(429, -1);
                exchange.close();
                return;
            }
            StringBuilder json = new StringBuilder("[[");
            String[] lines = answer.split("(?<=\n)");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) json.append(',');
                json.append("[").append(new com.google.gson.Gson().toJson(lines[i])).append(",\"src\",null,null,10]");
            }
            json.append("],null,\"en\"]");
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/translate_a/single";
        return new GoogleFreeProvider(() -> url, () -> 5000);
    }

    private static String perLine(String q) {
        StringBuilder out = new StringBuilder();
        String[] lines = q.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            out.append("TR ").append(lines[i]);
        }
        return out.toString();
    }

    @Test
    void shortSegmentsGoAsLinesOfOneRequest() throws Exception {
        List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
        GoogleFreeProvider google = googleServer(FreeProvidersTest::perLine, calls);
        List<String> out = google.translate(request("Collect iron ingots", "[[P0]]Gold[[P1]] ingots", "Open the chest"));
        assertEquals(List.of("TR Collect iron ingots", "TR [[P0]]Gold[[P1]] ingots", "TR Open the chest"), out);
        assertEquals(1, calls.size());
        assertEquals("gtx", calls.get(0).get("client"));
        assertEquals("en", calls.get(0).get("sl"));
        assertEquals("tr", calls.get(0).get("tl"));
        assertEquals("t", calls.get(0).get("dt"));
        assertEquals("google-free@127.0.0.1", google.id());
        assertTrue(google.isConfigured(), "no key needed");
    }

    @Test
    void mergedLinesFallBackToOneRequestPerSegment() throws Exception {
        List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
        // the "translation" joins the lines into one sentence: the line count no longer matches
        GoogleFreeProvider google = googleServer(q -> "TR " + q.replace('\n', ' '), calls);
        List<String> out = google.translate(request("Collect iron ingots", "Open the chest"));
        assertEquals(List.of("TR Collect iron ingots", "TR Open the chest"), out);
        assertEquals(3, calls.size(), "one joined try, then one request per segment");
    }

    @Test
    void floodProtectionIsReportedAsARateLimitWithAWait() throws Exception {
        GoogleFreeProvider google = googleServer(q -> null, new CopyOnWriteArrayList<>());
        ProviderException e = assertThrows(ProviderException.class, () -> google.translate(request("Collect iron ingots")));
        assertEquals(ProviderException.Kind.RATE_LIMIT, e.kind());
        assertTrue(e.retryAfterMillis() >= 30_000L);
    }

    @Test
    void unexpectedAnswersAreRejected() {
        assertThrows(ProviderException.class, () -> GoogleFreeProvider.parse("<html>captcha</html>"));
        assertThrows(ProviderException.class, () -> GoogleFreeProvider.parse("{}"));
    }

    @Test
    void theWholePipelineWorksWithTheKeylessService() throws Exception {
        List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
        GoogleFreeProvider google = googleServer(FreeProvidersTest::perLine, calls);
        var cache = new me.langyue.autotranslation.core.cache.TranslationCache(dir.resolve("cache.jsonl"), 1000);
        cache.load();
        var corrections = new me.langyue.autotranslation.core.cache.CorrectionStore(dir.resolve("c.json"));
        corrections.load();
        service = TranslationService.builder().settings(settings()).provider(() -> google).cache(cache).corrections(corrections)
                .coalesceShortTexts(true).build();
        Result r = lookupAndWait(service, plain("q:1", "Collect §6iron§r ingots"));
        assertEquals("TR Collect §6iron§r ingots", text(r, 0), "colour codes survive through the placeholder tokens");
    }

    // ----------------------------------------------------------------------------------- free-tier quota

    @Test
    void freeTierRateLimitAnswersAreUnderstood() {
        assertEquals(36_400L, OpenAiCompatibleProvider.retryDelayFromBody("Quota exceeded. Please retry in 36.4s."));
        assertEquals(86_400L, OpenAiCompatibleProvider.retryDelayFromBody("Rate limit reached. Please try again in 1m26.4s."));
        assertEquals(36_000L, OpenAiCompatibleProvider.retryDelayFromBody("{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\": \"36s\"}"));
        assertEquals(0L, OpenAiCompatibleProvider.retryDelayFromBody("no hint here"));
        assertTrue(OpenAiCompatibleProvider.isDailyQuota("\"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel-FreeTier\""));
        assertTrue(OpenAiCompatibleProvider.isDailyQuota("Rate limit reached for model on requests per day (RPD): Limit 1000"));
        assertTrue(OpenAiCompatibleProvider.isDailyQuota("Rate limit exceeded: free-models-per-day"));
        assertFalse(OpenAiCompatibleProvider.isDailyQuota("\"quotaId\": \"GenerateRequestsPerMinutePerProjectPerModel-FreeTier\""));
    }

    private OpenAiCompatibleProvider chatServer(Function<JsonObject, Integer> status, Function<JsonObject, String> body,
                                                List<JsonObject> seen, Map<String, String> extras) throws IOException {
        return chatServer(status, body, seen, extras, List.of(), 0.2);
    }

    private OpenAiCompatibleProvider chatServer(Function<JsonObject, Integer> status, Function<JsonObject, String> body,
                                                List<JsonObject> seen, Map<String, String> extras, List<String> fallbacks,
                                                double temperature) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            seen.add(request);
            byte[] bytes = body.apply(request).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.apply(request), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        OpenAiSettings s = new OpenAiSettings("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "free-model",
                Secret.of("AIzaSyExampleExampleExample123"), 5000, 5000, temperature, 1024, true, extras, fallbacks);
        return new OpenAiCompatibleProvider(() -> s);
    }

    private static String answer(JsonObject request) {
        String user = request.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
        StringBuilder out = new StringBuilder("{\"translations\":[");
        var segments = JsonParser.parseString(user).getAsJsonObject().getAsJsonArray("segments");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) out.append(',');
            out.append(new com.google.gson.Gson().toJson("TR| " + segments.get(i).getAsString()));
        }
        out.append("]}");
        JsonObject message = new JsonObject();
        message.addProperty("content", out.toString());
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
        choices.add(choice);
        root.add("choices", choices);
        return root.toString();
    }

    @Test
    void anExhaustedDailyQuotaIsNotRetriedAndPausesTheService() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        String body = "{\"error\":{\"code\":429,\"message\":\"You exceeded your current quota.\",\"status\":\"RESOURCE_EXHAUSTED\","
                + "\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}";
        OpenAiCompatibleProvider provider = chatServer(r -> 429, r -> body, seen, Map.of());
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Collect iron ingots")));
        assertEquals(ProviderException.Kind.QUOTA, e.kind());
        assertFalse(e.retryable());

        var cache = new me.langyue.autotranslation.core.cache.TranslationCache(dir.resolve("cache.jsonl"), 1000);
        cache.load();
        var corrections = new me.langyue.autotranslation.core.cache.CorrectionStore(dir.resolve("c.json"));
        corrections.load();
        service = TranslationService.builder().settings(settings()).provider(() -> provider).cache(cache).corrections(corrections).build();
        int before = seen.size();
        lookupAndWait(service, plain("q:1", "Collect iron ingots"));
        assertEquals(before + 1, seen.size(), "no retries against a used-up daily quota");
        assertTrue(service.stats().pausedUntilMillis() > System.currentTimeMillis() + 10 * 60_000L, "paused for a long while");
        assertTrue(service.stats().lastError().contains("quota"));
        Result later = service.lookup(plain("q:2", "Collect gold ingots"), null);
        assertEquals(State.FAILED, later.state(), "while paused nothing new is sent");
        assertEquals(before + 1, seen.size());
    }

    @Test
    void anExtraParameterTheServiceRefusesIsDroppedAndTheCallRepeated() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = chatServer(
                r -> r.has("reasoning_effort") ? 400 : 200,
                r -> r.has("reasoning_effort") ? "{\"error\":{\"message\":\"Unknown parameter: 'reasoning_effort'\"}}" : answer(r),
                seen, Map.of("reasoning_effort", "none"));
        assertEquals(List.of("TR| Collect iron ingots"), provider.translate(request("Collect iron ingots")));
        assertEquals(2, seen.size());
        assertEquals("none", seen.get(0).get("reasoning_effort").getAsString(), "sent the first time");
        assertFalse(seen.get(1).has("reasoning_effort"), "left out after the refusal");
        assertEquals(List.of("TR| Open the chest"), provider.translate(request("Open the chest")));
        assertEquals(3, seen.size(), "remembered: no second refusal");
    }

    private static final String PER_DAY = "[{\"error\":{\"code\":429,\"message\":\"You exceeded your current quota. Please retry in 12s.\","
            + "\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}]";

    @Test
    void whenOneModelsDailyQuotaIsUsedUpTheNextModelTakesOver() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = chatServer(
                r -> r.get("model").getAsString().equals("free-model") ? 429 : 200,
                r -> r.get("model").getAsString().equals("free-model") ? PER_DAY : answer(r),
                seen, Map.of(), List.of("second-model"), 0.2);
        assertEquals("free-model", provider.model());
        assertEquals(List.of("TR| Collect iron ingots"), provider.translate(request("Collect iron ingots")));
        assertEquals(2, seen.size());
        assertEquals("second-model", seen.get(1).get("model").getAsString());
        assertEquals("second-model", provider.model(), "the used-up model is skipped (and cache keys follow the model in use)");
        provider.translate(request("Open the chest"));
        assertEquals(3, seen.size(), "no further request to the used-up model");
        assertEquals("second-model", seen.get(2).get("model").getAsString());
    }

    @Test
    void whenEveryModelIsUsedUpTheQuotaErrorReachesTheService() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = chatServer(r -> 429, r -> PER_DAY, seen, Map.of(), List.of("second-model"), 0.2);
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Collect iron ingots")));
        assertEquals(ProviderException.Kind.QUOTA, e.kind());
        assertTrue(e.retryAfterMillis() > 60 * 60_000L, "comes back much later: " + e.retryAfterMillis());
        assertEquals(2, seen.size(), "each model tried once");
        assertThrows(ProviderException.class, () -> provider.translate(request("Open the chest")));
        assertEquals(2, seen.size(), "used-up models are not asked again");
    }

    @Test
    void aWithdrawnModelIsReplacedByTheFallback() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = chatServer(
                r -> r.get("model").getAsString().equals("free-model") ? 404 : 200,
                r -> r.get("model").getAsString().equals("free-model") ? "[{\"error\":{\"code\":404,\"message\":\"no longer available to new users\"}}]" : answer(r),
                seen, Map.of(), List.of("second-model"), 0.2);
        assertEquals(List.of("TR| Collect iron ingots"), provider.translate(request("Collect iron ingots")));
        assertEquals("second-model", provider.model());
    }

    @Test
    void aSingleModelSetupReportsItsErrorUnchanged() throws Exception {
        OpenAiCompatibleProvider provider = chatServer(r -> 404, r -> "{\"error\":\"not found\"}", new CopyOnWriteArrayList<>(), Map.of());
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Collect iron ingots")));
        assertEquals(ProviderException.Kind.MODEL_UNAVAILABLE, e.kind());
        assertThrows(ProviderException.class, () -> provider.translate(request("Collect iron ingots")),
                "no skip list for a single model: a corrected address works at once");
    }

    @Test
    void theTemperatureIsLeftOutWhereTheServiceAdvisesSo() throws Exception {
        List<JsonObject> seen = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = chatServer(r -> 200, FreeProvidersTest::answer, seen, Map.of("reasoning_effort", "minimal"),
                List.of(), Double.NaN);
        provider.translate(request("Collect iron ingots"));
        assertFalse(seen.get(0).has("temperature"));
        assertEquals("minimal", seen.get(0).get("reasoning_effort").getAsString());
    }
}
