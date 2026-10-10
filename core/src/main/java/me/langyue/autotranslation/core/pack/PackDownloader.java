package me.langyue.autotranslation.core.pack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches the shared pack of a language and keeps a copy on disk, so that the pack keeps working offline and a start
 * only downloads the shards that changed. Only GET requests are made and nothing from the player is sent (the address
 * of the pack host sees an ordinary web request for a few static files).
 *
 * <p>The pack is untrusted input: every shard must match the SHA-256 in the manifest, sizes are bounded, and every
 * translation is checked against its original again before it is shown (see {@link PackValidator}).
 */
public final class PackDownloader {
    private static final Gson GSON = new Gson();

    private PackDownloader() {
    }

    /** Only https; plain http is allowed for the computer itself (tests, a self-hosted copy on the local network is not). */
    static boolean allowedUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") || u.startsWith("http://localhost") || u.startsWith("http://127.0.0.1")
                || u.startsWith("http://[::1]");
    }

    /**
     * @param baseUrl       the address the pack site is served from, for example {@code https://packs.example.com}
     * @param language      Minecraft's code, for example {@code tr_tr}
     * @param dir           where the local copy is kept; the language gets a folder of its own
     * @return the pack (possibly from the local copy only, possibly empty), or null if nothing is configured or usable
     */
    public static SharedPack sync(String baseUrl, String language, Path dir, int timeoutMillis) {
        if (!PackFormat.validLanguage(language) || !allowedUrl(baseUrl)) return null;
        String base = baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        Path local = dir.resolve(language);
        JsonObject manifest = null;
        try {
            Files.createDirectories(local);
            byte[] bytes = get(base + "/v1/" + language + "/manifest.json", PackFormat.MAX_MANIFEST_BYTES, timeoutMillis);
            JsonObject remote = parseManifest(bytes, language);
            if (remote != null) {
                download(base, language, remote, local, timeoutMillis);
                Files.write(local.resolve("manifest.json"), bytes);
                manifest = remote;
            }
        } catch (IOException | RuntimeException e) {
            CoreLog.info("Shared pack: not reachable (" + e.getMessage() + "); using the copy on this computer, if any");
        }
        if (manifest == null) {
            try {
                Path file = local.resolve("manifest.json");
                if (Files.exists(file)) manifest = parseManifest(Files.readAllBytes(file), language);
            } catch (IOException | RuntimeException e) {
                CoreLog.warn("Shared pack: the local copy cannot be read", e);
            }
        }
        if (manifest == null) return new SharedPack(language, new HashMap<String, String>());
        return new SharedPack(language, load(manifest, local));
    }

    private static JsonObject parseManifest(byte[] bytes, String language) {
        JsonObject o = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        if (o == null || !o.has("format") || o.get("format").getAsInt() != PackFormat.FORMAT
                || !language.equals(o.get("language").getAsString())) {
            return null;
        }
        JsonArray shards = o.getAsJsonArray("shards");
        for (JsonElement item : shards) {
            JsonObject shard = item.getAsJsonObject();
            if (!PackFormat.SHARD_NAME.matcher(shard.get("name").getAsString()).matches()
                    || !PackFormat.HASH.matcher(shard.get("sha256").getAsString()).matches()) {
                return null;
            }
        }
        return o;
    }

    private static void download(String base, String language, JsonObject manifest, Path local, int timeoutMillis) {
        for (JsonElement item : manifest.getAsJsonArray("shards")) {
            JsonObject shard = item.getAsJsonObject();
            String name = shard.get("name").getAsString();
            String sha = shard.get("sha256").getAsString();
            try {
                Path file = local.resolve(name);
                if (Files.exists(file) && sha.equals(PackFormat.sha256(Files.readAllBytes(file)))) continue;
                byte[] bytes = get(base + "/v1/" + language + "/" + name, PackFormat.MAX_SHARD_BYTES, timeoutMillis);
                if (!sha.equals(PackFormat.sha256(bytes))) {
                    CoreLog.warn("Shared pack: " + name + " does not match the manifest; ignored");
                    continue;
                }
                Path temp = local.resolve(name + ".tmp");
                Files.write(temp, bytes);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException e) {
                CoreLog.warn("Shared pack: could not fetch " + name + " (" + e.getMessage() + ")");
            }
        }
    }

    private static Map<String, String> load(JsonObject manifest, Path local) {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        for (JsonElement item : manifest.getAsJsonArray("shards")) {
            JsonObject shard = item.getAsJsonObject();
            Path file = local.resolve(shard.get("name").getAsString());
            try {
                if (!Files.exists(file)) continue;
                byte[] bytes = Files.readAllBytes(file);
                if (!shard.get("sha256").getAsString().equals(PackFormat.sha256(bytes))) continue;
                entries.putAll(PackFormat.decode(bytes));
            } catch (IOException | RuntimeException e) {
                CoreLog.warn("Shared pack: " + file.getFileName() + " cannot be read (" + e.getMessage() + ")");
            }
        }
        return entries;
    }

    private static byte[] get(String url, int maxBytes, int timeoutMillis) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty("User-Agent", "AutoTranslation-Pack/1");
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = connection.getInputStream()) {
                return PackFormat.readCapped(in, maxBytes);
            }
        } finally {
            connection.disconnect();
        }
    }
}
