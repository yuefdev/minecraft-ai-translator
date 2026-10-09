package me.langyue.autotranslation.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.architectury.event.events.client.ClientTickEvent;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.tr.Integrations;
import me.langyue.autotranslation.tr.TranslationRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Automated start-up check, only active with {@code -Dautotranslation.smoketest=true} (never in normal play).
 *
 * <p>Once the title screen is up it (1) checks that the mixins were really applied to the installed optional
 * mods, (2) drives the real FTB Quests / Patchouli classes through the translation pipeline against a local fake
 * provider, and (3) writes a JSON report and closes the game. When an optional mod is not installed it checks
 * that the game still started and that its hooks were skipped.
 */
public final class SmokeTest {
    public static final List<JsonObject> CHECKS = new ArrayList<>();
    private static int ticks = 0;
    private static boolean started = false;
    private static boolean finished = false;
    private static FakeOpenAiServer server;
    public static final List<Pending> WAITING = new ArrayList<>();

    /** A condition that is polled every client tick until it holds or its tick budget runs out. */
    public static final class Pending {
        final String name;
        final BooleanSupplier condition;
        int remaining;
        final Runnable onTimeout;

        public Pending(String name, BooleanSupplier condition, int deadlineTicks, Runnable onTimeout) {
            this.name = name;
            this.condition = condition;
            this.remaining = deadlineTicks;
            this.onTimeout = onTimeout;
        }
    }

    private SmokeTest() {
    }

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(SmokeTest::tick);
        AutoTranslation.LOGGER.info("Smoke test armed");
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
                    check("reached title screen", false, "timeout; screen=" + minecraft.screen + " overlay=" + minecraft.getOverlay());
                    finish(minecraft);
                }
                return;
            }
            WAITING.removeIf(p -> {
                if (p.condition.getAsBoolean()) {
                    check(p.name, true, "");
                    return true;
                }
                if (--p.remaining <= 0) {
                    p.onTimeout.run();
                    return true;
                }
                return false;
            });
            if (WAITING.isEmpty() && started && !finished) finish(minecraft);
        } catch (Throwable t) {
            check("smoke test itself", false, t.toString());
            finish(minecraft);
        }
    }

    // ------------------------------------------------------------------------------------------------ steps

    private static void begin(Minecraft minecraft) throws Exception {
        check("reached title screen", minecraft.screen instanceof TitleScreen, "screen=" + minecraft.screen);
        TranslationRuntime.service();
        check("translation runtime started", TranslationRuntime.service() != null, "");

        me.langyue.autotranslation.core.util.CoreLog.setDebug(true);
        server = new FakeOpenAiServer();
        try (java.net.Socket probe = new java.net.Socket()) {
            probe.connect(new java.net.InetSocketAddress("127.0.0.1", server.port()), 3000);
            check("fake provider endpoint is reachable from the game", true, server.describe());
        } catch (java.io.IOException e) {
            check("fake provider endpoint is reachable from the game", false, e + " | " + server.describe()
                    + " | preferIPv6=" + System.getProperty("java.net.preferIPv6Addresses")
                    + " preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack")
                    + " loopback=" + java.net.InetAddress.getLoopbackAddress());
        }
        var settings = AutoTranslation.CONFIG.turkish;
        settings.enabled = true;
        settings.translateQuests = true;
        settings.translateBooks = true;
        settings.translateGeneric = true;
        settings.showOriginal = false;
        settings.provider = "custom"; // the fake endpoint below, whatever the key looks like
        settings.endpoint = "http://127.0.0.1:" + server.port() + "/v1";
        settings.model = "smoke-model";
        settings.requestsPerMinute = 6000;
        AutoTranslation.CONFIG.translator = "OpenAI";
        minecraft.options.languageCode = "tr_tr";
        TranslationRuntime.setApiKey(Secret.of("sk-smoke-test-0123456789"));
        TranslationRuntime.applyConfig();
        check("provider configured against local fake endpoint", TranslationRuntime.providerConfigured(), "");

        checkMixinsApplied();
        checkGenericTranslation(minecraft);
        for (Integrations.SmokeCheck optional : Integrations.smokeChecks()) {
            checkOptionalMod(optional.modId(), optional.probeClass(), optional.functional());
        }
    }

    private static final String SAMPLE_KEY = "autotranslation.smoke.sample";
    private static final String SAMPLE_LITERAL = "Welcome back, brave explorer of the deep caves";

    /** A screen of "some other mod": it is not on any list, so only the default behaviour decides. */
    private static final class SmokeScreen extends net.minecraft.client.gui.screens.Screen {
        SmokeScreen() {
            super(Component.literal("smoke"));
        }
    }

    /**
     * The part that matters for "translate every mod": nothing here knows FTB Quests or Patchouli. A mod without a
     * Turkish language file and a mod that draws hard-coded English text must both end up in Turkish by themselves.
     */
    private static void checkGenericTranslation(Minecraft minecraft) {
        final boolean[] reloaded = {false};
        minecraft.getLanguageManager().setSelected("tr_tr");
        minecraft.options.languageCode = "tr_tr";
        minecraft.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
        WAITING.add(new Pending("generic: resources reloaded with Turkish selected", () -> reloaded[0], 2400,
                () -> check("generic: resources reloaded with Turkish selected", false, "the reload did not finish")));
        WAITING.add(new Pending("generic: language-file text of a mod without a Turkish file is translated (I18n.get)",
                () -> reloaded[0] && net.minecraft.client.resources.language.I18n.get(SAMPLE_KEY).startsWith("TR| Open the chest"), 1800,
                () -> check("generic: language-file text of a mod without a Turkish file is translated (I18n.get)", false,
                        "I18n.get -> " + net.minecraft.client.resources.language.I18n.get(SAMPLE_KEY)
                                + " | last error: " + TranslationRuntime.service().stats().lastError())));
        WAITING.add(new Pending("generic: hard-coded English text on a mod screen is translated without any per-screen setting",
                () -> {
                    // last of all: while an ordinary screen is open every other check would see doubly translated text
                    if (!reloaded[0] || WAITING.size() > 1) return false;
                    if (!(minecraft.screen instanceof SmokeScreen)) {
                        minecraft.setScreen(new SmokeScreen());
                        return false;
                    }
                    var contents = Component.literal(SAMPLE_LITERAL).getContents();
                    return contents instanceof net.minecraft.network.chat.contents.LiteralContents literal
                            && literal.text().startsWith("TR| Welcome back");
                }, 1800,
                () -> check("generic: hard-coded English text on a mod screen is translated without any per-screen setting", false,
                        "screen=" + minecraft.screen + " text=" + Component.literal(SAMPLE_LITERAL).getContents()
                                + " | last error: " + TranslationRuntime.service().stats().lastError())));
    }

    private static void checkMixinsApplied() {
        expectField("net.minecraft.client.resources.language.ClientLanguage", "autoTranslation$ready", "core: ClientLanguage mixin applied");
        expectField("net.minecraft.network.chat.MutableComponent", "at$shouldTranslate", "core: MutableComponent mixin applied");
    }

    private static void checkOptionalMod(String modId, String probeClass, Runnable functional) {
        boolean loaded = TranslationRuntime.isModLoaded(modId);
        boolean classPresent = classExists(probeClass);
        check(modId + ": detection agrees with classpath", loaded == classPresent, "loaded=" + loaded + " classPresent=" + classPresent);
        if (!loaded) {
            check(modId + ": absent, game started and hooks skipped", TranslationRuntime.hooksSeen().stream().noneMatch(h -> h.startsWith(modId)), "");
            return;
        }
        functional.run();
    }

    // ----------------------------------------------------------------------------------------------- helpers

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, SmokeTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static void expectField(String className, String field, String name) {
        try {
            Class.forName(className, false, SmokeTest.class.getClassLoader()).getDeclaredField(field);
            check(name, true, "");
        } catch (ReflectiveOperationException | LinkageError e) {
            check(name, false, e.toString());
        }
    }

    public static void expectMethod(String className, String fragment, String name) {
        try {
            Class<?> cls = Class.forName(className, false, SmokeTest.class.getClassLoader());
            boolean found = Arrays.stream(cls.getDeclaredMethods()).anyMatch(m -> m.getName().contains(fragment));
            check(name, found, found ? "" : "no merged handler containing '" + fragment + "' in " + className);
        } catch (ReflectiveOperationException | LinkageError e) {
            check(name, false, e.toString());
        }
    }

    public static void check(String name, boolean ok, String detail) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("ok", ok);
        if (!detail.isEmpty()) o.addProperty("detail", detail);
        CHECKS.add(o);
        AutoTranslation.LOGGER.info("[smoke] {} {}{}", ok ? "PASS" : "FAIL", name, detail.isEmpty() ? "" : " :: " + detail);
    }

    private static void finish(Minecraft minecraft) {
        if (finished) return;
        finished = true;
        boolean ok = CHECKS.stream().allMatch(c -> c.get("ok").getAsBoolean());
        JsonObject report = new JsonObject();
        report.addProperty("ok", ok);
        report.addProperty("providerRequests", server == null ? 0 : server.requests.get());
        report.addProperty("authorizationHeaderSent", server != null && server.lastAuthorization.startsWith("Bearer "));
        JsonArray array = new JsonArray();
        CHECKS.forEach(array::add);
        report.add("checks", array);
        report.addProperty("ftbquestsLoaded", TranslationRuntime.isModLoaded("ftbquests"));
        report.addProperty("patchouliLoaded", TranslationRuntime.isModLoaded("patchouli"));
        try {
            Path out = Path.of(System.getProperty("autotranslation.smoketest.output", "smoke-result.json"));
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception e) {
            AutoTranslation.LOGGER.error("Could not write the smoke test report", e);
        }
        if (server != null) server.close();
        AutoTranslation.LOGGER.info("[smoke] finished: {}", ok ? "ALL CHECKS PASSED" : "FAILURES");
        minecraft.stop();
    }
}
