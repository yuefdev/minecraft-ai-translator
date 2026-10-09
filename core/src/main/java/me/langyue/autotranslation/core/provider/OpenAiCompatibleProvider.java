package me.langyue.autotranslation.core.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Redactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translation through any OpenAI-compatible {@code /chat/completions} endpoint. Settings are read from a
 * supplier on every call, so changes made in the settings screen apply without a restart.
 *
 * <p>The provider uses {@link HttpURLConnection} (part of {@code java.base}, honours the JVM proxy settings),
 * never follows redirects (so the Authorization header cannot leak to another host) and never logs headers.
 */
public final class OpenAiCompatibleProvider implements TranslationProvider {
    private static final Pattern FENCE = Pattern.compile("(?s)^\\s*```[a-zA-Z]*\\s*(.*?)\\s*```\\s*$");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final Supplier<OpenAiSettings> settings;
    private volatile boolean jsonModeRejected = false;

    public OpenAiCompatibleProvider(Supplier<OpenAiSettings> settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "openai-compatible@" + settings.get().host();
    }

    @Override
    public String model() {
        return settings.get().model();
    }

    @Override
    public boolean isConfigured() {
        return settings.get().looksConfigured();
    }

    @Override
    public List<String> translate(ProviderRequest request) throws ProviderException {
        OpenAiSettings s = settings.get();
        if (!s.looksConfigured()) {
            throw new ProviderException(ProviderException.Kind.NOT_CONFIGURED,
                    "Translation provider is not configured (endpoint, model or API key missing)");
        }
        boolean useJsonMode = s.jsonResponseFormat() && !jsonModeRejected;
        try {
            return callOnce(s, request, useJsonMode);
        } catch (JsonModeRejected e) {
            jsonModeRejected = true;
            CoreLog.info("The endpoint rejected response_format; continuing without it");
            try {
                return callOnce(s, request, false);
            } catch (JsonModeRejected again) {
                throw new ProviderException(ProviderException.Kind.BAD_REQUEST, again.getMessage());
            }
        }
    }

    private List<String> callOnce(OpenAiSettings s, ProviderRequest request, boolean jsonMode)
            throws ProviderException, JsonModeRejected {
        byte[] body = buildBody(s, request, jsonMode).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(s.endpoint()).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(s.connectTimeoutMillis());
            connection.setReadTimeout(s.requestTimeoutMillis());
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AutoTranslation-Turkish");
            if (!s.apiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + s.apiKey().reveal());
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            if (status >= 300) {
                String errorBody = readBody(connection.getErrorStream());
                throw statusFailure(status, errorBody, connection.getHeaderField("Retry-After"), jsonMode);
            }
            return parseResponse(readBody(connection.getInputStream()), request.segments().size());
        } catch (SocketTimeoutException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT, "The translation service did not answer in time");
        } catch (UnknownHostException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Unknown host: " + s.host());
        } catch (ConnectException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect to " + s.host());
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Network error: " + Redactor.redact(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ProviderException statusFailure(int status, String errorBody, String retryAfter, boolean jsonMode)
            throws JsonModeRejected {
        String snippet = errorBody == null ? "" : errorBody.replaceAll("\\s+", " ");
        if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "...";
        String detail = "HTTP " + status + (snippet.isEmpty() ? "" : ": " + snippet);
        if (status == 401 || status == 403) {
            return new ProviderException(ProviderException.Kind.AUTH, "Authentication failed (" + detail + ")");
        }
        if (status == 429) {
            return new ProviderException(ProviderException.Kind.RATE_LIMIT, "Rate limited (" + detail + ")",
                    parseRetryAfter(retryAfter));
        }
        if (status == 408 || status == 504) {
            return new ProviderException(ProviderException.Kind.TIMEOUT, detail);
        }
        if (status >= 500) {
            return new ProviderException(ProviderException.Kind.SERVER, "Server error (" + detail + ")",
                    parseRetryAfter(retryAfter));
        }
        if (status == 400 && jsonMode && snippet.toLowerCase().contains("response_format")) {
            throw new JsonModeRejected(detail);
        }
        return new ProviderException(ProviderException.Kind.BAD_REQUEST, "Request rejected (" + detail + ")");
    }

    private static long parseRetryAfter(String header) {
        if (header == null) return 0L;
        try {
            return Math.min(120_000L, Long.parseLong(header.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static String buildBody(OpenAiSettings s, ProviderRequest request, boolean jsonMode) {
        JsonObject root = new JsonObject();
        root.addProperty("model", s.model());
        root.addProperty("temperature", s.temperature());
        if (s.maxOutputTokens() > 0) root.addProperty("max_tokens", s.maxOutputTokens());
        if (jsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            root.add("response_format", format);
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", PromptBuilder.systemPrompt(request.target(), request.strict())));
        messages.add(message("user", PromptBuilder.userPrompt(request)));
        root.add("messages", messages);
        return root.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    static List<String> parseResponse(String responseBody, int expected) throws ProviderException {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return parseTranslations(content, expected);
        } catch (ProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The response was not a chat completion");
        }
    }

    static List<String> parseTranslations(String content, int expected) throws ProviderException {
        String text = content == null ? "" : content.strip();
        Matcher fence = FENCE.matcher(text);
        if (fence.matches()) text = fence.group(1);
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(extractJson(text));
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The answer was not valid JSON");
        }
        JsonArray array;
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("translations")
                && parsed.getAsJsonObject().get("translations").isJsonArray()) {
            array = parsed.getAsJsonObject().getAsJsonArray("translations");
        } else if (parsed.isJsonArray()) {
            array = parsed.getAsJsonArray();
        } else {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The answer had no translations list");
        }
        if (array.size() != expected) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE,
                    "Expected " + expected + " translations but received " + array.size());
        }
        List<String> result = new ArrayList<>(expected);
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "A translation was not a string");
            }
            result.add(element.getAsString());
        }
        return result;
    }

    /** Models sometimes wrap the JSON in a sentence; take the outermost object/array. */
    private static String extractJson(String text) {
        int obj = text.indexOf('{');
        int arr = text.indexOf('[');
        int start = obj < 0 ? arr : arr < 0 ? obj : Math.min(obj, arr);
        if (start < 0) return text;
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = text.lastIndexOf(close);
        return end > start ? text.substring(start, end + 1) : text;
    }

    private static String readBody(InputStream in) throws IOException {
        if (in == null) return "";
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                if (out.size() + n > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** Internal signal: the endpoint does not support response_format, retry without it. */
    private static final class JsonModeRejected extends Exception {
        JsonModeRejected(String message) {
            super(message, null, false, false);
        }
    }
}
