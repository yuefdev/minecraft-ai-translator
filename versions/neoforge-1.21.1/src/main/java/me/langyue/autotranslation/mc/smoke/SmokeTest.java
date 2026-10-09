package me.langyue.autotranslation.mc.smoke;

import com.google.gson.JsonObject;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.service.Types.Content;
import me.langyue.autotranslation.core.smoke.FakeOpenAiServer;
import me.langyue.autotranslation.core.smoke.SmokeReport;
import me.langyue.autotranslation.core.util.CoreLog;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.engine.Engine;
import me.langyue.autotranslation.engine.EngineSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Automated start-up check, only active with {@code -Dautotranslation.smoketest=true} (never in normal play).
 *
 * <p>Once the title screen is up it (1) checks that the mixins were really applied to the installed optional
 * mods, (2) drives the real FTB Quests / Patchouli classes through the translation pipeline against a local fake
 * provider, and (3) writes a JSON report and closes the game. When an optional mod is not installed it checks that
 * the game still started and that its hooks were skipped.
 */
public final class SmokeTest {
    static final SmokeReport REPORT = new SmokeReport();
    private static int ticks = 0;
    private static boolean started = false;
    private static boolean finished = false;
    private static FakeOpenAiServer server;

    private SmokeTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        CoreLog.info("Smoke test armed");
    }

    private static void tick(Minecraft minecraft) {
        if (finished) return;
        ticks++;
        try {
            if (!started) {
                // a fresh game directory first shows the accessibility onboarding screen; skip it
                if (minecraft.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                    minecraft.options.onboardAccessibility = false;
                    minecraft.setScreen(new TitleScreen());
                    return;
                }
                boolean overlayDone = minecraft.getOverlay() == null;
                if (overlayDone && ticks > 60 && (minecraft.screen instanceof TitleScreen || ticks > 1200)) {
                    started = true;
                    begin(minecraft);
                } else if (ticks > 6000) {
                    REPORT.check("reached title screen", false, "timeout; screen=" + minecraft.screen + " overlay=" + minecraft.getOverlay());
                    finish(minecraft);
                }
                return;
            }
            if (REPORT.tickPending()) finish(minecraft);
        } catch (Throwable t) {
            REPORT.check("smoke test itself", false, t.toString());
            finish(minecraft);
        }
    }

    private static void begin(Minecraft minecraft) throws Exception {
        REPORT.check("reached title screen", minecraft.screen instanceof TitleScreen, "screen=" + minecraft.screen);
        Engine engine = Engine.get();
        REPORT.check("translation engine started", engine != null, "");

        CoreLog.setDebug(true);
        server = new FakeOpenAiServer();
        EngineSettings settings = engine.settings();
        settings.enabled = true;
        settings.translateQuests = true;
        settings.translateBooks = true;
        settings.translateGeneric = true;
        settings.showOriginal = false;
        settings.endpoint = "http://127.0.0.1:" + server.port() + "/v1";
        settings.model = "smoke-model";
        settings.requestsPerMinute = 6000;
        minecraft.options.languageCode = "tr_tr";
        engine.setApiKey(Secret.of("sk-smoke-test-0123456789"));
        engine.applyConfig();
        REPORT.check("provider configured against local fake endpoint", engine.providerConfigured(), "");

        // the generic path: engine -> service -> HTTP provider -> fake server -> engine
        final Content content = new Content("smoke", "test", "smoke:hello", "");
        engine.translate("Hello brave miner", content, MaskProfile.PLAIN);
        REPORT.waitFor("pipeline translates a plain text through the HTTP provider",
                () -> "TR| Hello brave miner".equals(Engine.get().translate("Hello brave miner", content, MaskProfile.PLAIN)), 600,
                () -> "no translation; last error: " + Engine.get().service().stats().lastError());

        checkOptional(engine, "ftbquests", "dev.ftb.mods.ftbquests.quest.QuestObjectBase", 0);
        checkOptional(engine, "patchouli", "vazkii.patchouli.client.book.BookPage", 1);
    }

    private static void checkOptional(Engine engine, String modId, String probeClass, int which) {
        boolean loaded = engine.isModLoaded(modId);
        boolean classPresent = classExists(probeClass);
        REPORT.check(modId + ": detection agrees with classpath", loaded == classPresent, "loaded=" + loaded + " classPresent=" + classPresent);
        if (!loaded) {
            boolean none = true;
            for (String hook : engine.hooksSeen()) if (hook.startsWith(modId)) none = false;
            REPORT.check(modId + ": absent, game started and hooks skipped", none, "");
            return;
        }
        if (which == 0) SmokeFtb.run(REPORT);
        else SmokePatchouli.run(REPORT);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, SmokeTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static void finish(Minecraft minecraft) {
        if (finished) return;
        finished = true;
        JsonObject report = REPORT.toJson(server);
        Engine engine = Engine.get();
        report.addProperty("ftbquestsLoaded", engine.isModLoaded("ftbquests"));
        report.addProperty("patchouliLoaded", engine.isModLoaded("patchouli"));
        Path out = Paths.get(System.getProperty("autotranslation.smoketest.output", "smoke-result.json"));
        REPORT.write(out, report);
        if (server != null) server.close();
        CoreLog.info("[smoke] finished: " + (REPORT.allOk() ? "ALL CHECKS PASSED" : "FAILURES"));
        minecraft.stop();
    }
}
