package me.langyue.autotranslation.legacy;

import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.EngineSettings;
import me.langyue.autotranslation.engine.Platform;
import me.langyue.autotranslation.engine.SettingsStore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/** Client side start-up of the 1.12.2 version: creates the engine; the patched hooks do the rest. */
@Mod(modid = AutoTranslationMod.MOD_ID, name = "AutoTranslation", version = "@VERSION@", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]")
public class AutoTranslationMod {
    public static final String MOD_ID = "autotranslation";
    static final Logger LOGGER = LogManager.getLogger("AutoTranslation");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CoreLog.setSink(new CoreLog.Sink() {
            @Override
            public void log(CoreLog.Level level, String message, Throwable error) {
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
            }
        });
        final Path dir = event.getModConfigurationDirectory().getParentFile().toPath().resolve("AutoTranslation");
        Platform platform = new Platform() {
            @Override
            public Path dataDir() {
                return dir;
            }

            @Override
            public String languageCode() {
                Minecraft minecraft = Minecraft.getMinecraft();
                return minecraft == null || minecraft.gameSettings == null ? "en_us" : minecraft.gameSettings.language.toLowerCase();
            }

            @Override
            public void runOnClientThread(Runnable task) {
                Minecraft.getMinecraft().addScheduledTask(task);
            }

            @Override
            public boolean isModLoaded(String modId) {
                return Loader.isModLoaded(modId);
            }
        };
        EngineSettings settings = new SettingsStore(dir.resolve("settings.json")).load();
        Engine engine = Engine.start(platform, settings);
        MinecraftForge.EVENT_BUS.register(this);
        engine.registerRefresher(new Runnable() {
            @Override
            public void run() {
                refreshOpenScreen();
            }
        });
        if (Boolean.getBoolean("autotranslation.smoketest")) {
            SmokeTest.register();
        }
        CoreLog.info("AutoTranslation (Turkish pipeline) started for Minecraft 1.12.2");
    }

    private boolean pretranslationStarted;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Engine current = Engine.get();
            if (current == null) return;
            if (!pretranslationStarted) {
                // the first tick: the game has finished loading, so the language files of all mods can be read
                pretranslationStarted = true;
                if (!Boolean.getBoolean("autotranslation.smoketest")) current.startPretranslation(new LanguageFiles());
            }
            current.tick();
        }
    }

    /** Rebuilds a quest or book screen so that text translated in the meantime replaces the original. */
    static void refreshOpenScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) return;
        String screen = mc.currentScreen.getClass().getName();
        if (screen.startsWith("betterquesting.") || screen.startsWith("com.feed_the_beast.ftbquests.")
                || screen.startsWith("vazkii.patchouli.")) {
            net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc);
            mc.currentScreen.setWorldAndResolution(mc, res.getScaledWidth(), res.getScaledHeight());
        }
    }
}
