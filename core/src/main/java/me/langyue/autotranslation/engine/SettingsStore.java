package me.langyue.autotranslation.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.langyue.autotranslation.core.util.AtomicFiles;
import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes {@link EngineSettings} as {@code settings.json} (for versions without a config library). */
public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;

    public SettingsStore(Path file) {
        this.file = file;
    }

    public EngineSettings load() {
        if (Files.exists(file)) {
            try {
                EngineSettings loaded = GSON.fromJson(Compat.readString(file), EngineSettings.class);
                if (loaded != null) {
                    if (loaded.wordBlacklist == null) loaded.wordBlacklist = new java.util.ArrayList<String>();
                    if (loaded.sharedPackUrl == null) loaded.sharedPackUrl = EngineSettings.DEFAULT_PACK_URL;
                    return loaded;
                }
            } catch (IOException e) {
                CoreLog.warn("Could not read " + file + ", using defaults", e);
            } catch (RuntimeException e) {
                CoreLog.warn("Settings file " + file + " is not valid, using defaults", e);
            }
        }
        EngineSettings defaults = new EngineSettings();
        defaults.sharedPackUrl = EngineSettings.DEFAULT_PACK_URL;
        save(defaults);
        return defaults;
    }

    public void save(EngineSettings settings) {
        try {
            AtomicFiles.write(file, GSON.toJson(settings));
        } catch (IOException e) {
            CoreLog.warn("Could not write " + file, e);
        }
    }
}
