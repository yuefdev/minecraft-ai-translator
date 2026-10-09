package me.langyue.autotranslation.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.provider.OpenAiCompatibleProvider;
import me.langyue.autotranslation.core.provider.OpenAiSettings;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.TranslationContext;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Redactor;
import me.langyue.autotranslation.core.util.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP code path against a local fake server; no external network is involved. */
class ProviderTest {
    private static final String KEY = "sk-test-1234567890abcdef";

    private interface Handler {
        void handle(HttpExchange exchange, String body) throws IOException;
    }

    private HttpServer server;
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            handler.get().handle(exchange, body);
        });
        server.start();
        Redactor.register(Secret.of(KEY));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        Redactor.clearRegistered();
    }

    private OpenAiSettings settings(int requestTimeoutMillis) {
        return new OpenAiSettings("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model",
                Secret.of(KEY), 2_000, requestTimeoutMillis, 0.2, 512, true);
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String completion(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject root = new JsonObject();
        root.add("choices", choices);
        return root.toString();
    }

    private static ProviderRequest request(String... segments) {
        TranslationContext context = new TranslationContext("ftbquests", "quest description", "Getting Started",
                "previous paragraph", "next paragraph", Map.of("Chest", "Sandık"));
        return new ProviderRequest(LanguageProfile.TURKISH, List.of(segments), context, false);
    }

    @Test
    void sendsModelCredentialsContextAndGlossary() throws Exception {
        handler.set((ex, body) -> reply(ex, 200, completion("{\"translations\":[\"Merhaba\",\"Dünya\"]}")));
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));

        List<String> result = provider.translate(request("Hello", "World"));

        assertEquals(List.of("Merhaba", "Dünya"), result);
        assertEquals("Bearer " + KEY, authHeaders.get(0));
        JsonObject sent = JsonParser.parseString(bodies.get(0)).getAsJsonObject();
        assertEquals("test-model", sent.get("model").getAsString());
        JsonArray messages = sent.getAsJsonArray("messages");
        String system = messages.get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(system.contains("Turkish") && system.contains("tr"), "target language must be named");
        assertTrue(system.contains("[[P0]]"), "marker rules must be explained");
        JsonObject user = JsonParser.parseString(messages.get(1).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
        assertEquals("ftbquests", user.get("source_mod").getAsString());
        assertEquals("quest description", user.get("content_type").getAsString());
        assertEquals("Getting Started", user.get("title_context").getAsString());
        assertEquals("previous paragraph", user.get("context_before").getAsString());
        assertEquals("Sandık", user.getAsJsonObject("glossary").get("Chest").getAsString());
        assertEquals(2, user.getAsJsonArray("segments").size());
    }

    @Test
    void acceptsFencedAndWrappedJson() throws Exception {
        handler.set((ex, body) -> reply(ex, 200, completion("Here you go:\n```json\n{\"translations\":[\"Merhaba\"]}\n```")));
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        assertEquals(List.of("Merhaba"), provider.translate(request("Hello")));
    }

    @Test
    void authenticationFailureIsNotRetryableAndNeverLeaksTheKey() {
        handler.set((ex, body) -> reply(ex, 401, "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}"));
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.AUTH, e.kind());
        assertFalse(e.retryable());
        assertFalse(e.getMessage().contains(KEY), e.getMessage());
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        handler.set((ex, body) -> {
            ex.getResponseHeaders().add("Retry-After", "2");
            reply(ex, 429, "{\"error\":\"slow down\"}");
        });
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.RATE_LIMIT, e.kind());
        assertTrue(e.retryable());
        assertEquals(2_000L, e.retryAfterMillis());
    }

    @Test
    void serverErrorsAreRetryable() {
        handler.set((ex, body) -> reply(ex, 503, "unavailable"));
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.SERVER, e.kind());
        assertTrue(e.retryable());
    }

    @Test
    void brokenResponsesAreReportedAsBadResponse() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        for (String content : List.of("I cannot translate that.", "{\"translations\":[\"only one\"]}", "{\"translations\":[1,2]}", "[]")) {
            handler.set((ex, body) -> reply(ex, 200, completion(content)));
            ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello", "World")), content);
            assertEquals(ProviderException.Kind.BAD_RESPONSE, e.kind(), content);
        }
        handler.set((ex, body) -> reply(ex, 200, "<html>not a chat completion</html>"));
        assertEquals(ProviderException.Kind.BAD_RESPONSE,
                assertThrows(ProviderException.class, () -> provider.translate(request("Hello"))).kind());
    }

    @Test
    void slowServerTimesOut() {
        handler.set((ex, body) -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException ignored) {
            }
            reply(ex, 200, completion("{\"translations\":[\"x\"]}"));
        });
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(300));
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.TIMEOUT, e.kind());
    }

    @Test
    void endpointThatRejectsResponseFormatIsRetriedWithoutIt() throws Exception {
        handler.set((ex, body) -> {
            if (body.contains("response_format")) {
                reply(ex, 400, "{\"error\":\"Unsupported parameter: response_format\"}");
            } else {
                reply(ex, 200, completion("{\"translations\":[\"Merhaba\"]}"));
            }
        });
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        assertEquals(List.of("Merhaba"), provider.translate(request("Hello")));
        assertEquals(2, bodies.size());
        provider.translate(request("Hello"));
        assertEquals(3, bodies.size(), "after the first rejection response_format is not sent again");
        assertFalse(bodies.get(2).contains("response_format"));
    }

    @Test
    void redirectsAreNotFollowed() {
        handler.set((ex, body) -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/steal");
            reply(ex, 302, "");
        });
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(3_000));
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.BAD_REQUEST, e.kind());
    }

    @Test
    void unconfiguredProviderFailsWithoutNetworkAccess() {
        OpenAiSettings noKey = new OpenAiSettings("https://api.openai.com/v1", "m", Secret.empty(), 1000, 1000, 0.2, 100, true);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> noKey);
        assertFalse(provider.isConfigured());
        ProviderException e = assertThrows(ProviderException.class, () -> provider.translate(request("Hello")));
        assertEquals(ProviderException.Kind.NOT_CONFIGURED, e.kind());
        assertTrue(bodies.isEmpty());

        OpenAiSettings local = new OpenAiSettings("http://localhost:11434/v1", "llama", Secret.empty(), 1000, 1000, 0.2, 100, true);
        assertTrue(new OpenAiCompatibleProvider(() -> local).isConfigured(), "local servers usually need no key");
    }

    @Test
    void endpointAcceptsBaseUrlOrFullPath() {
        assertEquals("https://x.example/v1/chat/completions",
                new OpenAiSettings("https://x.example/v1/", "m", Secret.empty(), 1, 1, 0, 1, true).endpoint());
        assertEquals("https://x.example/v1/chat/completions",
                new OpenAiSettings("https://x.example/v1/chat/completions", "m", Secret.empty(), 1, 1, 0, 1, true).endpoint());
    }

    @Test
    void cacheKeyProviderIdentifiesHostButNeverTheKey() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(() -> settings(1000));
        assertTrue(provider.id().startsWith("openai-compatible@127.0.0.1"));
        assertFalse(provider.id().contains(KEY));
    }

    // ----- credentials never reach logs --------------------------------------------------------------------

    @Test
    void settingsAndSecretsAreMaskedInToString() {
        assertFalse(settings(1000).toString().contains(KEY));
        assertEquals("****", Secret.of(KEY).toString());
        assertEquals("<unset>", Secret.empty().toString());
    }

    @Test
    void logLinesAndExceptionMessagesAreRedacted() {
        List<String> lines = new CopyOnWriteArrayList<>();
        CoreLog.setSink((level, message, error) -> {
            lines.add(message);
            if (error != null) lines.add(error.toString());
        });
        try {
            CoreLog.warn("Request failed with key " + KEY + " and header Authorization: Bearer " + KEY);
            CoreLog.error("boom", new IllegalStateException("server said: bad key " + KEY));
            CoreLog.info("url https://api.example/v1?api_key=" + KEY + "&x=1");
            CoreLog.warn("unregistered token sk-live-ABCDEFGH12345678 in text");
        } finally {
            CoreLog.setSink((level, message, error) -> {
            });
        }
        String all = String.join("\n", lines);
        assertFalse(all.contains(KEY), all);
        assertFalse(all.contains("ABCDEFGH12345678"), all);
        assertTrue(all.contains("****"));
    }

    @Test
    void providerExceptionMessagesAreRedactedOnConstruction() {
        ProviderException e = new ProviderException(ProviderException.Kind.AUTH, "denied for " + KEY);
        assertFalse(e.getMessage().contains(KEY));
    }
}
