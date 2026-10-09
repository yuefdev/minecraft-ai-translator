package me.langyue.autotranslation.core.glossary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.util.AtomicFiles;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes the user's glossary file ({@code glossary.json}: a flat {@code {"English": "Türkçe"}} object).
 * When the file does not exist yet it is created from the starter list shipped inside the mod.
 */
public final class GlossaryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String STARTER_RESOURCE = "/autotranslation/glossary-tr.json";

    private final Path file;

    public GlossaryStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public Map<String, String> load() {
        if (Files.exists(file)) {
            try {
                return parse(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                CoreLog.warn("Could not read glossary " + file + ", using the starter list", e);
            }
        }
        Map<String, String> starter = loadStarter();
        try {
            save(starter);
        } catch (IOException e) {
            CoreLog.warn("Could not write initial glossary " + file, e);
        }
        return starter;
    }

    public void save(Map<String, String> terms) throws IOException {
        AtomicFiles.write(file, GSON.toJson(new LinkedHashMap<>(terms)));
    }

    public static Map<String, String> loadStarter() {
        try (InputStream in = GlossaryStore.class.getResourceAsStream(STARTER_RESOURCE)) {
            if (in == null) return new LinkedHashMap<>();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[2048];
                int n;
                while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
                return parse(sb.toString());
            }
        } catch (IOException | RuntimeException e) {
            CoreLog.warn("Could not read the starter glossary", e);
            return new LinkedHashMap<>();
        }
    }

    static Map<String, String> parse(String json) {
        Map<String, String> terms = new LinkedHashMap<>();
        JsonObject object = GSON.fromJson(json, JsonObject.class);
        if (object == null) return terms;
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            if (e.getValue().isJsonPrimitive()) terms.put(e.getKey(), e.getValue().getAsString());
        }
        return terms;
    }
}
