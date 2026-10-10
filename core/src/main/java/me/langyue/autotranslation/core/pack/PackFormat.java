package me.langyue.autotranslation.core.pack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.Compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The files of a shared pack. A pack lives under {@code v1/<language>/} and consists of
 * <ul>
 *   <li>{@code manifest.json}: the format, the language and the name, size and SHA-256 of every shard;</li>
 *   <li>{@code shard-0.jsonl.gz} … {@code shard-f.jsonl.gz}: one JSON object per line,
 *       {@code {"s":"<source hash>","t":"<translation>"}}. A text goes to the shard named by the first hex digit of
 *       its source hash.</li>
 * </ul>
 * The source hash is the one of the translation cache ({@code CacheKey.sourceHash()}), so a pack holds no source text
 * and no key names, only hashes and translations.
 */
public final class PackFormat {
    public static final int FORMAT = 1;
    static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,4}");
    static final Pattern SHARD_NAME = Pattern.compile("shard-[0-9a-f]\\.jsonl\\.gz");
    static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    /** Bounds for what is read from the network or from disk. */
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    static final int MAX_SHARD_BYTES = 32 * 1024 * 1024;
    static final int MAX_SHARD_TEXT_BYTES = 128 * 1024 * 1024;
    private static final Gson GSON = new Gson();

    private PackFormat() {
    }

    public static boolean validLanguage(String language) {
        return language != null && LANGUAGE.matcher(language).matches();
    }

    static String shardName(String sourceHash) {
        return "shard-" + sourceHash.charAt(0) + ".jsonl.gz";
    }

    /** Deterministic: entries sorted by hash. */
    static byte[] encode(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            for (Map.Entry<String, String> e : new TreeMap<String, String>(entries).entrySet()) {
                JsonObject line = new JsonObject();
                line.addProperty("s", e.getKey());
                line.addProperty("t", e.getValue());
                gzip.write((GSON.toJson(line) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toByteArray();
    }

    /** Lines that are not valid entries are skipped. */
    static Map<String, String> decode(byte[] shard) throws IOException {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        long budget = MAX_SHARD_TEXT_BYTES;
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(shard));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                budget -= line.length() + 1L;
                if (budget < 0) throw new IOException("pack shard is too large when unpacked");
                if (Compat.isBlank(line)) continue;
                try {
                    JsonObject o = GSON.fromJson(line, JsonObject.class);
                    String hash = o.get("s").getAsString();
                    String text = o.get("t").getAsString();
                    if (HASH.matcher(hash).matches() && !text.isEmpty() && text.length() <= PackValidator.MAX_LENGTH) {
                        entries.put(hash, text);
                    }
                } catch (RuntimeException e) {
                    // a damaged line is skipped
                }
            }
        }
        return entries;
    }

    static String sha256(byte[] bytes) {
        try {
            return Compat.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    static byte[] readCapped(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            if (out.size() + n > max) throw new IOException("response is larger than " + max + " bytes");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
