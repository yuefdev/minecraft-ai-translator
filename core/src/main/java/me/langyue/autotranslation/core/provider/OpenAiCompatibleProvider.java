package me.langyue.autotranslation.core.provider;

import me.langyue.autotranslation.core.util.Compat;
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

    /** Request parameters that models/servers have refused so far; they are left out from then on. */
    private static final List<String> NEGOTIABLE_PARAMETERS = Compat.listOf("response_format", "temperature", "max_tokens", "max_completion_tokens");

    private final Supplier<OpenAiSettings> settings;
    /** Parameters refused so far, per endpoint and model (another service or model may well accept them). */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> unsupportedByTarget =
            new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.Set<String> unsupported(OpenAiSettings s) {
        String target = s.endpoint() + "|" + s.model();
        java.util.Set<String> set = unsupportedByTarget.get(target);
        if (set == null) {
            unsupportedByTarget.putIfAbsent(target, java.util.concurrent.ConcurrentHashMap.<String>newKeySet());
            set = unsupportedByTarget.get(target);
        }
        return set;
    }

    public OpenAiCompatibleProvider(Supplier<OpenAiSettings> settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "openai-compatible@" + settings.get().host();
    }

    /** Models that answered "daily quota used up" or "not available": skipped until the time stored here. */
    private final java.util.concurrent.ConcurrentHashMap<String, Skip> skipped = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long QUOTA_SKIP_MILLIS = 2 * 60 * 60_000L;
    private static final long WITHDRAWN_SKIP_MILLIS = 24 * 60 * 60_000L;

    private static final class Skip {
        final long until;
        final ProviderException.Kind kind;
        final String message;

        Skip(long until, ProviderException.Kind kind, String message) {
            this.until = until;
            this.kind = kind;
            this.message = message;
        }
    }

    private static String skipKey(OpenAiSettings s, String model) {
        return s.endpoint() + "|" + model;
    }

    /**
     * The configured model. It is part of every cache key, so it stays the same while a fallback model of the same
     * service answers (otherwise texts would be translated again each time the service switches models).
     */
    @Override
    public String model() {
        return settings.get().model();
    }

    private static List<String> candidates(OpenAiSettings s) {
        List<String> list = new ArrayList<>();
        if (s.model() != null && !Compat.isBlank(s.model())) list.add(Compat.strip(s.model()));
        for (String fallback : s.fallbackModels()) {
            if (fallback != null && !Compat.isBlank(fallback) && !list.contains(Compat.strip(fallback))) list.add(Compat.strip(fallback));
        }
        return list;
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
        List<String> candidates = candidates(s);
        if (candidates.size() <= 1) return negotiate(s, request); // one model: every error goes straight to the caller
        Skip latest = null;
        for (String candidate : candidates) {
            long now = System.currentTimeMillis();
            Skip skip = skipped.get(skipKey(s, candidate));
            if (skip != null && skip.until > now) {
                if (latest == null || skip.until < latest.until) latest = skip;
                continue;
            }
            try {
                return negotiate(candidate.equals(s.model()) ? s : s.withModel(candidate), request);
            } catch (ProviderException e) {
                if (e.kind() != ProviderException.Kind.QUOTA && e.kind() != ProviderException.Kind.MODEL_UNAVAILABLE) throw e;
                long wait = e.kind() == ProviderException.Kind.QUOTA ? Math.max(e.retryAfterMillis(), QUOTA_SKIP_MILLIS) : WITHDRAWN_SKIP_MILLIS;
                Skip mark = new Skip(now + wait, e.kind(), e.getMessage());
                skipped.put(skipKey(s, candidate), mark);
                if (latest == null || mark.until < latest.until) latest = mark;
                CoreLog.info("Model " + candidate + " is not usable now (" + e.kind() + "); trying the next model of this service");
            }
        }
        // every model of the service is used up (or withdrawn): report the one that comes back first
        long wait = Math.max(0L, latest.until - System.currentTimeMillis());
        throw new ProviderException(latest.kind, latest.message, latest.kind == ProviderException.Kind.QUOTA ? wait : 0L);
    }

    private List<String> negotiate(OpenAiSettings s, ProviderRequest request) throws ProviderException {
        // Some models refuse optional parameters (temperature, response_format, max_tokens...). Learn which ones
        // and retry immediately without them; bounded, so a misbehaving server cannot cause a loop.
        for (int attempt = 0; ; attempt++) {
            try {
                return callOnce(s, request);
            } catch (ParameterRejected e) {
                if (attempt >= NEGOTIABLE_PARAMETERS.size() + s.extraParameters().size() || !unsupported(s).add(e.parameter)) {
                    throw new ProviderException(ProviderException.Kind.BAD_REQUEST, e.getMessage());
                }
                CoreLog.info("The endpoint rejected the parameter '" + e.parameter + "'; continuing without it");
            }
        }
    }

    private List<String> callOnce(OpenAiSettings s, ProviderRequest request)
            throws ProviderException, ParameterRejected {
        java.util.Set<String> refused = unsupported(s);
        byte[] body = buildBody(s, request, refused).getBytes(StandardCharsets.UTF_8);
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
                throw statusFailure(status, errorBody, connection.getHeaderField("Retry-After"), droppable(s, refused), refused);
            }
            return parseResponse(readBody(connection.getInputStream()), request.segments().size());
        } catch (SocketTimeoutException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT, "The translation service did not answer in time");
        } catch (UnknownHostException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Unknown host: " + s.host());
        } catch (ConnectException e) {
            CoreLog.debug("Connect to " + s.host() + " failed: " + e);
            throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect to " + s.host());
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Network error: " + Redactor.redact(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Optional fields this request carried, in the order they are given up when a service refuses "something". */
    private static List<String> droppable(OpenAiSettings s, java.util.Set<String> refused) {
        List<String> list = new ArrayList<>();
        if (s.jsonResponseFormat() && !refused.contains("response_format")) list.add("response_format");
        for (String extra : s.extraParameters().keySet()) if (!refused.contains(extra)) list.add(extra);
        return list;
    }

    private static final Pattern AUTH_HINT = Pattern.compile(
            "(?i)api[ _-]?key|auth(?:entication|orization)? (?:key|header)|invalid[ _-]?api[ _-]?key|api_key_invalid|unauthenticated|ACCESS_TOKEN_TYPE_UNSUPPORTED");

    private ProviderException statusFailure(int status, String errorBody, String retryAfter, List<String> droppable,
                                            java.util.Set<String> refused) throws ParameterRejected {
        String snippet = errorBody == null ? "" : errorBody.replaceAll("\\s+", " ");
        if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "...";
        String detail = "HTTP " + status + (snippet.isEmpty() ? "" : ": " + snippet);
        String body = errorBody == null ? "" : errorBody;
        if (status == 401 || status == 403 || (status == 400 && AUTH_HINT.matcher(body).find())) {
            // Gemini answers a wrong key with 400 "Please pass a valid API key"
            return new ProviderException(ProviderException.Kind.AUTH, "The API key was not accepted (" + detail + ")");
        }
        if (body.contains("User location is not supported") || body.contains("FAILED_PRECONDITION") && body.contains("location")) {
            return new ProviderException(ProviderException.Kind.BAD_REQUEST,
                    "This service is not available from your location (" + detail + ")");
        }
        if (status == 413) {
            // Groq: "Request too large ... tokens per minute": the request (incl. max_tokens) exceeds a per-minute budget
            return new ProviderException(ProviderException.Kind.RATE_LIMIT, "Request too large for the per-minute limit (" + detail + ")",
                    Math.max(retryDelayFromBody(body), 20_000L));
        }
        if (status == 429) {
            long wait = parseRetryAfter(retryAfter);
            if (wait == 0L) wait = retryDelayFromBody(errorBody);
            if (isDailyQuota(errorBody)) {
                return new ProviderException(ProviderException.Kind.QUOTA,
                        "The free daily quota of this service is used up (" + detail + ")", wait);
            }
            return new ProviderException(ProviderException.Kind.RATE_LIMIT, "Rate limited (" + detail + ")", wait);
        }
        if (status == 404) {
            return new ProviderException(ProviderException.Kind.MODEL_UNAVAILABLE, "Model or address not found (" + detail + ")");
        }
        if (status == 408 || status == 504) {
            return new ProviderException(ProviderException.Kind.TIMEOUT, detail);
        }
        if (status >= 500) {
            return new ProviderException(ProviderException.Kind.SERVER, "Server error (" + detail + ")",
                    parseRetryAfter(retryAfter));
        }
        if (status == 400 || status == 422) {
            String lower = body.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("json_validate_failed")) {
                // Groq: the model produced invalid JSON this time; another attempt usually works
                return new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The service could not produce valid JSON (" + detail + ")");
            }
            List<String> candidates = new ArrayList<>(droppable);
            for (String parameter : NEGOTIABLE_PARAMETERS) if (!candidates.contains(parameter)) candidates.add(parameter);
            for (String parameter : candidates) {
                if (lower.contains(parameter.toLowerCase(java.util.Locale.ROOT)) && !refused.contains(parameter)) {
                    throw new ParameterRejected(parameter, detail);
                }
            }
            // refused without naming the field (e.g. "JSON mode is not enabled", a list of allowed values):
            // give up one optional field and try again; bounded by the number of optional fields
            if (!droppable.isEmpty()) throw new ParameterRejected(droppable.get(0), detail);
        }
        return new ProviderException(ProviderException.Kind.BAD_REQUEST, "Request rejected (" + detail + ")");
    }

    private static final Pattern DAILY = Pattern.compile("(?i)per[ _-]?day|daily|PerDay|per[ _-]?month|monthly");
    private static final Pattern RETRY_IN = Pattern.compile(
            "(?i)(?:retry in|try again in|retryDelay\"?\\s*:\\s*\")\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m(?!s))?\\s*(?:(\\d+(?:\\.\\d+)?)s)?");

    /** Free tiers say which allowance ran out; only the daily/monthly ones are worth a long pause. */
    public static boolean isDailyQuota(String body) {
        return body != null && DAILY.matcher(body).find();
    }

    /** "Please retry in 36.4s", "try again in 1m26.4s", {@code "retryDelay": "36s"}; 0 if none. */
    public static long retryDelayFromBody(String body) {
        if (body == null) return 0L;
        Matcher m = RETRY_IN.matcher(body);
        while (m.find()) {
            if (m.group(1) == null && m.group(2) == null && m.group(3) == null) continue;
            double seconds = 0;
            if (m.group(1) != null) seconds += Integer.parseInt(m.group(1)) * 3600.0;
            if (m.group(2) != null) seconds += Integer.parseInt(m.group(2)) * 60.0;
            if (m.group(3) != null) seconds += Double.parseDouble(m.group(3));
            return (long) Math.ceil(seconds * 1000.0);
        }
        return 0L;
    }

    private static long parseRetryAfter(String header) {
        if (header == null) return 0L;
        try {
            return Math.min(120_000L, Long.parseLong(header.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static String buildBody(OpenAiSettings s, ProviderRequest request, java.util.Set<String> unsupported) {
        JsonObject root = new JsonObject();
        root.addProperty("model", s.model());
        if (!unsupported.contains("temperature") && !Double.isNaN(s.temperature())) root.addProperty("temperature", s.temperature());
        if (s.maxOutputTokens() > 0) {
            if (!unsupported.contains("max_tokens")) root.addProperty("max_tokens", s.maxOutputTokens());
            else if (!unsupported.contains("max_completion_tokens")) root.addProperty("max_completion_tokens", s.maxOutputTokens());
        }
        for (java.util.Map.Entry<String, String> extra : s.extraParameters().entrySet()) {
            if (!unsupported.contains(extra.getKey())) root.addProperty(extra.getKey(), extra.getValue());
        }
        if (s.jsonResponseFormat() && !unsupported.contains("response_format")) {
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
            JsonObject root = Compat.parseJson(responseBody).getAsJsonObject();
            JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonElement finish = choice.get("finish_reason");
            String reason = finish == null || finish.isJsonNull() ? "" : finish.getAsString();
            JsonObject message = choice.getAsJsonObject("message");
            JsonElement contentElement = message == null ? null : message.get("content");
            if (contentElement == null || contentElement.isJsonNull() || Compat.isBlank(contentElement.getAsString())) {
                // a safety filter (Gemini: SAFETY / PROHIBITED_CONTENT, OpenAI: content_filter): asking again only uses quota
                throw new ProviderException(ProviderException.Kind.BAD_REQUEST,
                        "The service returned no text" + (reason.isEmpty() ? "" : " (finish reason " + reason + ")"));
            }
            if ("length".equalsIgnoreCase(reason)) {
                throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The answer was cut off (output limit reached)");
            }
            return parseTranslations(contentElement.getAsString(), expected);
        } catch (ProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "The response was not a chat completion");
        }
    }

    static List<String> parseTranslations(String content, int expected) throws ProviderException {
        String text = content == null ? "" : Compat.strip(content);
        Matcher fence = FENCE.matcher(text);
        if (fence.matches()) text = fence.group(1);
        JsonElement parsed;
        try {
            parsed = Compat.parseJson(extractJson(text));
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
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                if (out.size() + n > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Internal signal: the endpoint refused an optional request parameter; retry without it. */
    private static final class ParameterRejected extends Exception {
        final String parameter;

        ParameterRejected(String parameter, String message) {
            super(message, null, false, false);
            this.parameter = parameter;
        }
    }
}
