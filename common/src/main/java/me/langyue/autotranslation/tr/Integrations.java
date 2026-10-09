package me.langyue.autotranslation.tr;

import me.langyue.autotranslation.AutoTranslation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for the optional integrations (FTB Quests, Patchouli). The platform module registers them; each one is
 * initialised only if its mod is installed. This keeps every class that mentions an optional mod's types out of the
 * common module, so the common code never needs those mods on its class path.
 */
public final class Integrations {
    /** Start-up check that is run by the smoke test when the mod is installed. */
    public record SmokeCheck(String modId, String probeClass, Runnable functional) {
    }

    private record Entry(String modId, Runnable init) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final List<SmokeCheck> SMOKE = new CopyOnWriteArrayList<>();

    private Integrations() {
    }

    public static void register(String modId, Runnable init) {
        ENTRIES.add(new Entry(modId, init));
    }

    public static void registerSmokeCheck(String modId, String probeClass, Runnable functional) {
        SMOKE.add(new SmokeCheck(modId, probeClass, functional));
    }

    public static List<SmokeCheck> smokeChecks() {
        return SMOKE;
    }

    /** Runs the initialiser of every registered integration whose mod is loaded. */
    public static void initAll() {
        for (Entry entry : ENTRIES) {
            if (TranslationRuntime.isModLoaded(entry.modId())) {
                try {
                    entry.init().run();
                } catch (Throwable t) {
                    AutoTranslation.LOGGER.error("Integration '{}' failed to initialise and is disabled", entry.modId(), t);
                }
            } else {
                AutoTranslation.LOGGER.info("Optional integration '{}' skipped: mod not installed", entry.modId());
            }
        }
    }
}
