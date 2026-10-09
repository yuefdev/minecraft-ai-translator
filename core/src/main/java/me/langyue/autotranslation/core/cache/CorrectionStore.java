package me.langyue.autotranslation.core.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.AtomicFiles;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The user's own corrections. They take precedence over everything else (existing Turkish text, cache and new
 * automatic translations). A correction can be global (applies wherever this exact English text appears) or
 * scoped to one piece of content, in which case the scoped one wins.
 */
public final class CorrectionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Correction(String source, String translation, String scope) {
    }

    private final Path file;
    private final Map<String, Correction> byKey = new ConcurrentHashMap<>();
    private volatile long revision = 0;

    public CorrectionStore(Path file) {
        this.file = file;
    }

    public void load() {
        byKey.clear();
        if (!Files.exists(file)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || !root.has("corrections")) return;
            for (JsonElement element : root.getAsJsonArray("corrections")) {
                JsonObject o = element.getAsJsonObject();
                String scope = o.has("scope") && !o.get("scope").isJsonNull() ? o.get("scope").getAsString() : null;
                Correction c = new Correction(o.get("source").getAsString(), o.get("translation").getAsString(), scope);
                byKey.put(keyOf(c.source(), c.scope()), c);
            }
        } catch (IOException | RuntimeException e) {
            CoreLog.warn("Could not read corrections " + file, e);
        }
        revision++;
    }

    /** Scoped correction first, then the global one. */
    public String find(String source, String scope) {
        if (byKey.isEmpty() || source == null) return null;
        if (scope != null) {
            Correction scoped = byKey.get(keyOf(source, scope));
            if (scoped != null) return scoped.translation();
        }
        Correction global = byKey.get(keyOf(source, null));
        return global == null ? null : global.translation();
    }

    public void put(String source, String translation, String scope) throws IOException {
        byKey.put(keyOf(source, scope), new Correction(source, translation, scope));
        revision++;
        save();
    }

    public void remove(String source, String scope) throws IOException {
        if (byKey.remove(keyOf(source, scope)) != null) {
            revision++;
            save();
        }
    }

    public List<Correction> all() {
        List<Correction> list = new ArrayList<>(byKey.values());
        list.sort((a, b) -> a.source().compareToIgnoreCase(b.source()));
        return list;
    }

    /** Incremented on every change so callers can drop UI caches built from corrections. */
    public long revision() {
        return revision;
    }

    private void save() throws IOException {
        JsonArray array = new JsonArray();
        for (Correction c : all()) {
            JsonObject o = new JsonObject();
            o.addProperty("source", c.source());
            o.addProperty("translation", c.translation());
            if (c.scope() != null) o.addProperty("scope", c.scope());
            array.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("corrections", array);
        AtomicFiles.write(file, GSON.toJson(root));
    }

    private static String keyOf(String source, String scope) {
        return Hashing.sha256(source) + "|" + (scope == null ? "" : scope);
    }
}
