package me.langyue.autotranslation.core.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.Redactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Keyless machine translation through Google Translate's public web endpoint ({@code translate_a/t?client=gtx}),
 * the same service the original AutoTranslation used by default. No account and no key, but also no context, no
 * glossary and no guarantee: the endpoint is unofficial (Google's robots.txt excludes it) and Google blocks addresses
 * that send too much. It is the zero-setup fallback; the free AI presets give better and more reliable Turkish.
 *
 * <p>All segments of a batch go in one POST as repeated {@code q} fields; the answer is a JSON array of strings in
 * the same order. The masker's tokens are sent as {@code ⟦0⟧}, {@code ⟦R0⟧}…{@code ⟦/R0⟧}, which this engine keeps
 * in place (tested: {@code [[P0]]}-style brackets are not guaranteed). Whatever still breaks is caught by the usual
 * validation, and the original text is shown instead.
 */
public final class GoogleFreeProvider implements TranslationProvider {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    /** A block (captcha page) usually lasts minutes to hours: ask again after this long. */
    static final long BLOCK_PAUSE_MILLIS = 30 * 60_000L;

    private static final java.util.regex.Pattern OUR_TOKEN = java.util.regex.Pattern.compile("\\[\\[\\s*(/?)\\s*([PR])\\s*(\\d+)\\s*]]");
    private static final java.util.regex.Pattern MT_TOKEN = java.util.regex.Pattern.compile("⟦\\s*(/?)\\s*(R?)\\s*(\\d+)\\s*⟧");

    private final Supplier<String> endpoint;
    private final IntSupplier timeoutMillis;

    public GoogleFreeProvider(Supplier<String> endpoint, IntSupplier timeoutMillis) {
        this.endpoint = endpoint;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public String id() {
        return "google-free@" + host(endpoint.get());
    }

    @Override
    public String model() {
        return "gtx";
    }

    @Override
    public boolean isConfigured() {
        String url = endpoint.get();
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    @Override
    public List<String> translate(ProviderRequest request) throws ProviderException {
        List<String> segments = request.segments();
        StringBuilder form = new StringBuilder();
        try {
            for (String segment : segments) {
                if (form.length() > 0) form.append('&');
                form.append("q=").append(URLEncoder.encode(toEngineTokens(segment), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "UTF-8 is not supported");
        }
        List<String> answers = parse(post(form.toString(), request.target().providerCode()));
        if (answers.size() != segments.size()) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE,
                    "Google Translate returned " + answers.size() + " texts for " + segments.size());
        }
        List<String> result = new ArrayList<String>(answers.size());
        for (String answer : answers) result.add(fromEngineTokens(answer));
        return result;
    }

    /** {@code [[P0]]} to {@code ⟦0⟧}, {@code [[R1]]} to {@code ⟦R1⟧}, {@code [[/R1]]} to {@code ⟦/R1⟧}. */
    static String toEngineTokens(String text) {
        java.util.regex.Matcher m = OUR_TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String kind = m.group(2).equals("P") ? "" : "R";
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("⟦" + m.group(1) + kind + m.group(3) + "⟧"));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String fromEngineTokens(String text) {
        java.util.regex.Matcher m = MT_TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String kind = m.group(2).isEmpty() ? "P" : "R";
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("[[" + m.group(1) + kind + m.group(3) + "]]"));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    private String post(String form, String target) throws ProviderException {
        String base = endpoint.get();
        byte[] body;
        String url;
        try {
            body = form.getBytes("UTF-8");
            url = base + "?client=gtx&sl=en&tl=" + URLEncoder.encode(target, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "UTF-8 is not supported");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            int timeout = Math.max(5000, timeoutMillis.getAsInt());
            connection.setConnectTimeout(Math.min(10000, timeout));
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (AutoTranslation Minecraft mod)");
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400 || status == 403) {
                // a redirect to google.com/sorry (captcha): this address is blocked for a while
                throw new ProviderException(ProviderException.Kind.QUOTA,
                        "Google Translate is temporarily blocking requests from this address (HTTP " + status + ")", BLOCK_PAUSE_MILLIS);
            }
            if (status == 429) {
                throw new ProviderException(ProviderException.Kind.RATE_LIMIT,
                        "Google Translate asks to slow down (HTTP 429)", 60_000L);
            }
            if (status >= 500) {
                throw new ProviderException(ProviderException.Kind.SERVER, "Google Translate server error (HTTP " + status + ")");
            }
            if (status >= 400) {
                throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "Google Translate refused the request (HTTP " + status + ")");
            }
            return readBody(connection.getInputStream());
        } catch (SocketTimeoutException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT, "Google Translate did not answer in time");
        } catch (UnknownHostException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Unknown host: " + host(base));
        } catch (ConnectException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Could not connect to " + host(base));
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Kind.NETWORK, "Network error: " + Redactor.redact(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * {@code ["Demir Kazma","Elmas Kılıç"]}; with automatic source detection each entry is
     * {@code ["Demir Kazma","en"]}. A captcha page or anything else is a block, not a translation.
     */
    public static List<String> parse(String body) throws ProviderException {
        JsonArray array;
        try {
            JsonElement root = Compat.parseJson(body);
            array = root.getAsJsonArray();
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.QUOTA,
                    "Google Translate answered with something other than a translation (probably a block page)", BLOCK_PAUSE_MILLIS);
        }
        List<String> out = new ArrayList<String>(array.size());
        try {
            for (JsonElement entry : array) {
                if (entry.isJsonArray()) entry = entry.getAsJsonArray().get(0);
                out.add(entry.getAsString());
            }
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "Google Translate sent an unexpected answer");
        }
        return out;
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
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static String host(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "unknown" : host;
        } catch (RuntimeException e) {
            return "invalid";
        }
    }
}
