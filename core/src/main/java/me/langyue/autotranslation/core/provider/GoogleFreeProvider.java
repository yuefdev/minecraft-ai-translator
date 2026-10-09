package me.langyue.autotranslation.core.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.CoreLog;
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
 * Keyless machine translation through Google Translate's public web endpoint ({@code translate_a/single?client=gtx}),
 * the same service the original AutoTranslation used by default. No account and no key, but also no context, no
 * glossary and no guaranteed availability: it is the free fallback, the AI presets give better Turkish.
 *
 * <p>Several short segments are sent as lines of one request; if the answer does not come back with the same number
 * of lines, each segment is sent on its own. Placeholder tokens ({@code [[P0]]}) usually survive; whatever does not
 * is caught by the usual validation, and the original text is shown instead.
 */
public final class GoogleFreeProvider implements TranslationProvider {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    /** Longest text sent in one request (characters before URL encoding). */
    public static final int MAX_REQUEST_CHARS = 1800;

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
        String target = request.target().providerCode();
        List<String> result = new ArrayList<String>(segments.size());
        if (segments.size() > 1 && canJoin(segments)) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) joined.append('\n');
                joined.append(segments.get(i));
            }
            String answer = call(joined.toString(), target);
            String[] lines = answer.split("\n", -1);
            if (lines.length == segments.size()) {
                for (String line : lines) result.add(line.trim());
                return result;
            }
            CoreLog.debug("Machine translation merged or split lines (" + lines.length + " for " + segments.size()
                    + "); translating the segments one by one");
        }
        for (String segment : segments) result.add(call(segment, target).trim());
        return result;
    }

    private static boolean canJoin(List<String> segments) {
        int chars = 0;
        for (String s : segments) {
            if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) return false;
            chars += s.length() + 1;
        }
        return chars <= MAX_REQUEST_CHARS;
    }

    private String call(String text, String target) throws ProviderException {
        String base = endpoint.get();
        String url;
        try {
            url = base + "?client=gtx&dt=t&sl=en&tl=" + URLEncoder.encode(target, "UTF-8") + "&q=" + URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "UTF-8 is not supported");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            int timeout = Math.max(5000, timeoutMillis.getAsInt());
            connection.setConnectTimeout(Math.min(10000, timeout));
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (AutoTranslation Minecraft mod)");
            int status = connection.getResponseCode();
            if (status == 429 || status == 403 || (status >= 300 && status < 400)) {
                // Google answers floods from one address with 429 (or a captcha page): back off for a while
                throw new ProviderException(ProviderException.Kind.RATE_LIMIT,
                        "Google Translate is limiting requests from this address (HTTP " + status + ")", 60_000L);
            }
            if (status >= 500) {
                throw new ProviderException(ProviderException.Kind.SERVER, "Google Translate server error (HTTP " + status + ")");
            }
            if (status >= 400) {
                throw new ProviderException(ProviderException.Kind.BAD_REQUEST, "Google Translate refused the request (HTTP " + status + ")");
            }
            return parse(readBody(connection.getInputStream()));
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

    /** {@code [[["Merhaba","Hello",null,null,10],["dünya","world",...]],null,"en",...]}: join the first column. */
    public static String parse(String body) throws ProviderException {
        try {
            JsonElement root = Compat.parseJson(body);
            JsonArray sentences = root.getAsJsonArray().get(0).getAsJsonArray();
            StringBuilder out = new StringBuilder();
            for (JsonElement sentence : sentences) {
                if (!sentence.isJsonArray() || sentence.getAsJsonArray().size() == 0) continue;
                JsonElement translated = sentence.getAsJsonArray().get(0);
                if (translated != null && translated.isJsonPrimitive()) out.append(translated.getAsString());
            }
            return out.toString();
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.BAD_RESPONSE, "Google Translate sent an unexpected answer");
        }
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
