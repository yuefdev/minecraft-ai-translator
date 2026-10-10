package me.langyue.autotranslation.core.pack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.Compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the files of a shared pack from translation caches ({@code AutoTranslation/cache/translations.jsonl}) of
 * players who have translated a modpack. Only plain texts of language files and interface text go in; quest and book
 * texts (styled runs) and anything else stay in the player's cache. Running it again on the same output folder adds
 * to the pack: entries already in it are kept unless the new cache has the same text.
 *
 * <pre>java -cp autotranslation.jar me.langyue.autotranslation.core.pack.PackBuilder cache.jsonl tr_tr pack-site</pre>
 */
public final class PackBuilder {
    private static final Gson GSON = new Gson();

    private PackBuilder() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: PackBuilder <translations.jsonl> <language, e.g. tr_tr> <output folder>");
            System.exit(2);
        }
        int count = build(Paths.get(args[0]), args[1], Paths.get(args[2]));
        System.out.println("Pack " + args[1] + ": " + count + " translations in " + Paths.get(args[2]).resolve("v1").resolve(args[1]));
    }

    /** @return the number of translations in the pack afterwards */
    public static int build(Path cacheFile, String language, Path outputFolder) throws IOException {
        if (!PackFormat.validLanguage(language)) throw new IOException("not a language code like tr_tr: " + language);
        Path dir = outputFolder.resolve("v1").resolve(language);
        Map<String, String> entries = new TreeMap<String, String>();
        entries.putAll(existing(dir));

        Map<String, Long> newest = new HashMap<String, Long>();
        Map<String, String> fresh = new HashMap<String, String>();
        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Compat.isBlank(line)) continue;
                try {
                    JsonObject o = GSON.fromJson(line, JsonObject.class);
                    if (!language.equals(o.get("l").getAsString())) continue;
                    String scope = o.get("c").getAsString();
                    if (!scope.startsWith("lang:") && !scope.equals("ui")) continue;
                    JsonObject value = o.getAsJsonObject("v");
                    JsonArray runs = value.has("r") ? value.getAsJsonArray("r") : null;
                    if (runs != null && runs.size() > 0) continue;
                    String hash = o.get("s").getAsString();
                    String text = value.get("t").getAsString();
                    long timestamp = o.get("ts").getAsLong();
                    if (!PackFormat.HASH.matcher(hash).matches() || text.isEmpty() || text.length() > PackValidator.MAX_LENGTH) continue;
                    Long seen = newest.get(hash);
                    if (seen == null || timestamp >= seen) {
                        newest.put(hash, timestamp);
                        fresh.put(hash, text);
                    }
                } catch (RuntimeException e) {
                    // a damaged line is skipped
                }
            }
        }
        entries.putAll(fresh);
        write(dir, language, entries);
        return entries.size();
    }

    private static Map<String, String> existing(Path dir) throws IOException {
        Map<String, String> entries = new HashMap<String, String>();
        if (!Files.isDirectory(dir)) return entries;
        try (DirectoryStream<Path> shards = Files.newDirectoryStream(dir, "shard-*.jsonl.gz")) {
            for (Path shard : shards) {
                if (PackFormat.SHARD_NAME.matcher(shard.getFileName().toString()).matches()) {
                    entries.putAll(PackFormat.decode(Files.readAllBytes(shard)));
                }
            }
        }
        return entries;
    }

    private static void write(Path dir, String language, Map<String, String> entries) throws IOException {
        Files.createDirectories(dir);
        Map<String, Map<String, String>> byShard = new TreeMap<String, Map<String, String>>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String name = PackFormat.shardName(e.getKey());
            Map<String, String> shard = byShard.get(name);
            if (shard == null) {
                shard = new TreeMap<String, String>();
                byShard.put(name, shard);
            }
            shard.put(e.getKey(), e.getValue());
        }
        JsonArray list = new JsonArray();
        for (Map.Entry<String, Map<String, String>> shard : byShard.entrySet()) {
            byte[] bytes = PackFormat.encode(shard.getValue());
            Path file = dir.resolve(shard.getKey());
            Path temp = dir.resolve(shard.getKey() + ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            JsonObject item = new JsonObject();
            item.addProperty("name", shard.getKey());
            item.addProperty("sha256", PackFormat.sha256(bytes));
            item.addProperty("count", shard.getValue().size());
            list.add(item);
        }
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "shard-*.jsonl.gz")) {
            for (Path file : old) {
                if (!byShard.containsKey(file.getFileName().toString())) Files.delete(file);
            }
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", PackFormat.FORMAT);
        manifest.addProperty("language", language);
        manifest.addProperty("count", entries.size());
        manifest.add("shards", list);
        Files.write(dir.resolve("manifest.json"), GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
    }
}
