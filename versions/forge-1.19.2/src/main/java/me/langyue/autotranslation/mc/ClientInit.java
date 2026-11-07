package me.langyue.autotranslation.mc;

import com.mojang.logging.LogUtils;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.EngineSettings;
import me.langyue.autotranslation.engine.Platform;
import me.langyue.autotranslation.engine.SettingsStore;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Client side start-up: creates the engine and enables the integrations of the mods that are installed.
 * Loader neutral: the Forge/NeoForge and the Fabric entry points pass in what differs between the loaders.
 */
public final class ClientInit {
    static final Logger LOGGER = LogUtils.getLogger();

    private ClientInit() {
    }

    /**
     * @param gameDir      the game directory
     * @param isModLoaded  whether a mod with the given id is installed
     * @param onClientTick registers a task that runs at the end of every client tick
     */
    public static void init(Path gameDir, Predicate<String> isModLoaded, Consumer<Runnable> onClientTick) {
        CoreLog.setSink((level, message, error) -> {
            switch (level) {
                case DEBUG:
                    LOGGER.debug(message, error);
                    break;
                case INFO:
                    LOGGER.info(message, error);
                    break;
                case WARN:
                    LOGGER.warn(message, error);
                    break;
                default:
                    LOGGER.error(message, error);
                    break;
            }
        });
        final Path dir = gameDir.resolve("AutoTranslation");
        Platform platform = new Platform() {
            @Override
            public Path dataDir() {
                return dir;
            }

            @Override
            public String languageCode() {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft == null || minecraft.options == null ? "en_us" : minecraft.options.languageCode;
            }

            @Override
            public void runOnClientThread(Runnable task) {
                Minecraft.getInstance().execute(task);
            }

            @Override
            public boolean isModLoaded(String modId) {
                return isModLoaded.test(modId);
            }
        };
        EngineSettings settings = new SettingsStore(dir.resolve("settings.json")).load();
        Engine engine = Engine.start(platform, settings);
        onClientTick.accept(() -> {
            Engine current = Engine.get();
            if (current != null) current.tick();
        });

        if (engine.isModLoaded("ftbquests")) {
            me.langyue.autotranslation.mc.ftb.FtbBootstrap.init();
        }
        if (engine.isModLoaded("patchouli")) {
            me.langyue.autotranslation.mc.patchouli.PatchouliBootstrap.init();
        }
        if (Boolean.getBoolean("autotranslation.smoketest")) {
            me.langyue.autotranslation.mc.smoke.SmokeTest.register(onClientTick);
        }
        CoreLog.info("AutoTranslation (Turkish pipeline) started for Minecraft 1.19.2");
        LOGGER.info("AutoTranslation started; data folder: {}", dir);
    }
}
