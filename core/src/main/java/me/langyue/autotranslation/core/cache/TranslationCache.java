package me.langyue.autotranslation.core.cache;

import me.langyue.autotranslation.core.util.Compat;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.mask.RestoreResult;
import me.langyue.autotranslation.core.util.AtomicFiles;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent cache of validated translations. Lookups are served from memory (never touching the disk or the
 * network), so cached translations keep working offline and are safe to call from the render thread. Writes are
 * appended to a JSON-lines file; the file is compacted when it grows beyond the configured size.
 */
public final class TranslationCache {
    private static final Gson GSON = new Gson();

    private static final class Entry {
        private final StoredTranslation value;
        private final long timestamp;

        public Entry(StoredTranslation value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public StoredTranslation value() {
            return value;
        }

        public long timestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry other = (Entry) o;
            return java.util.Objects.equals(value, other.value) && timestamp == other.timestamp;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(value, timestamp);
        }

        @Override
        public String toString() {
            return "Entry[" + "value=" + value + ", " + "timestamp=" + timestamp + "]";
        }
    }

    private final Path file;
    private final int maxEntries;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private final AtomicLong appendedSinceCompaction = new AtomicLong();
    private volatile int skippedCorruptLines = 0;

    public TranslationCache(Path file, int maxEntries) {
        this.file = file;
        this.maxEntries = Math.max(100, maxEntries);
    }

    /** Reads the cache file. Corrupt lines (for example from a crash while writing) are skipped. */
    public void load() {
        synchronized (ioLock) {
            entries.clear();
            skippedCorruptLines = 0;
            if (!Files.exists(file)) return;
            long lines = 0;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Compat.isBlank(line)) continue;
                    lines++;
                    try {
                        JsonObject o = GSON.fromJson(line, JsonObject.class);
                        entries.put(o.get("k").getAsString(),
                                new Entry(readValue(o.getAsJsonObject("v")), o.get("ts").getAsLong()));
                    } catch (RuntimeException e) {
                        skippedCorruptLines++;
                    }
                }
            } catch (IOException | RuntimeException e) {
                CoreLog.warn("Could not read translation cache " + file, e);
            }
            if (skippedCorruptLines > 0) {
                CoreLog.warn("Skipped " + skippedCorruptLines + " unreadable cache lines in " + file.getFileName());
            }
            if (lines > entries.size() * 2L || entries.size() > maxEntries) {
                compactLocked();
            }
        }
    }

    public StoredTranslation get(CacheKey key) {
        Entry entry = entries.get(key.fingerprint());
        return entry == null ? null : entry.value();
    }

    public boolean contains(CacheKey key) {
        return entries.containsKey(key.fingerprint());
    }

    public int size() {
        return entries.size();
    }

    public int skippedCorruptLines() {
        return skippedCorruptLines;
    }

    public void put(CacheKey key, StoredTranslation value) {
        long now = System.currentTimeMillis();
        String fingerprint = key.fingerprint();
        entries.put(fingerprint, new Entry(value, now));
        JsonObject o = new JsonObject();
        o.addProperty("k", fingerprint);
        o.addProperty("s", key.sourceHash());
        o.addProperty("l", key.targetLanguage());
        o.addProperty("c", key.contentId());
        o.addProperty("p", key.provider());
        o.addProperty("m", key.model());
        o.addProperty("g", key.glossaryVersion());
        o.addProperty("pv", key.promptVersion());
        o.add("v", writeValue(value));
        o.addProperty("ts", now);
        synchronized (ioLock) {
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Compat.writeString(file, GSON.toJson(o) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (appendedSinceCompaction.incrementAndGet() > maxEntries / 2L && entries.size() > maxEntries) {
                    compactLocked();
                }
            } catch (IOException e) {
                CoreLog.warn("Could not write translation cache " + file, e);
            }
        }
    }

    /** Rewrites the file with exactly the live entries (dropping the oldest ones above the size limit). */
    public void compact() {
        synchronized (ioLock) {
            compactLocked();
        }
    }

    private void compactLocked() {
        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, Entry> e) -> e.getValue().timestamp()).reversed());
        if (sorted.size() > maxEntries) {
            for (Map.Entry<String, Entry> dropped : sorted.subList(maxEntries, sorted.size())) {
                entries.remove(dropped.getKey());
            }
            sorted = new ArrayList<>(sorted.subList(0, maxEntries));
        }
        // Key fields beyond the fingerprint are only informative; a compacted line keeps what the loader needs.
        StringBuilder out = new StringBuilder();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Map.Entry<String, Entry> e = sorted.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("k", e.getKey());
            o.add("v", writeValue(e.getValue().value()));
            o.addProperty("ts", e.getValue().timestamp());
            out.append(GSON.toJson(o)).append('\n');
        }
        try {
            AtomicFiles.write(file, out.toString());
            appendedSinceCompaction.set(0);
        } catch (IOException e) {
            CoreLog.warn("Could not compact translation cache " + file, e);
        }
    }

    private static JsonObject writeValue(StoredTranslation value) {
        JsonObject v = new JsonObject();
        v.addProperty("t", value.text());
        if (value.hasRuns()) {
            JsonArray runs = new JsonArray();
            for (RestoreResult.Run run : value.runs()) {
                JsonObject r = new JsonObject();
                r.addProperty("i", run.styleIndex());
                r.addProperty("t", run.text());
                runs.add(r);
            }
            v.add("r", runs);
        }
        return v;
    }

    private static StoredTranslation readValue(JsonObject v) {
        String text = v.get("t").getAsString();
        List<RestoreResult.Run> runs = new ArrayList<>();
        if (v.has("r")) {
            for (com.google.gson.JsonElement element : v.getAsJsonArray("r")) {
                JsonObject r = element.getAsJsonObject();
                runs.add(new RestoreResult.Run(r.get("i").getAsInt(), r.get("t").getAsString()));
            }
        }
        return new StoredTranslation(text, Compat.listCopyOf(runs));
    }
}
