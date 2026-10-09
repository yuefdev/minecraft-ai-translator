package me.langyue.autotranslation.legacy;

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
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Automated start-up check, only active with {@code -Dautotranslation.smoketest=true} (never in normal play).
 * Everything that touches the optional mods goes through reflection, so this class can be compiled and loaded
 * without them.
 */
public final class SmokeTest {
    private static final SmokeReport REPORT = new SmokeReport();
    private static int ticks = 0;
    private static boolean started = false;
    private static boolean finished = false;
    private static FakeOpenAiServer server;

    private SmokeTest() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new SmokeTest());
        CoreLog.info("Smoke test armed");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        ticks++;
        try {
            if (!started) {
                if (minecraft.currentScreen instanceof GuiMainMenu && ticks > 60) {
                    started = true;
                    begin(minecraft);
                } else if (ticks > 6000) {
                    REPORT.check("reached title screen", false, "timeout; screen=" + minecraft.currentScreen);
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
        REPORT.check("reached title screen", minecraft.currentScreen instanceof GuiMainMenu, "screen=" + minecraft.currentScreen);
        final Engine engine = Engine.get();
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
        minecraft.gameSettings.language = "tr_tr";
        engine.setApiKey(Secret.of("sk-smoke-test-0123456789"));
        engine.applyConfig();
        REPORT.check("provider configured against local fake endpoint", engine.providerConfigured(), "");

        final Content content = new Content("smoke", "test", "smoke:hello", "");
        engine.translate("Hello brave miner", content, MaskProfile.PLAIN);
        REPORT.waitFor("pipeline translates a plain text through the HTTP provider",
                () -> "TR| Hello brave miner".equals(Engine.get().translate("Hello brave miner", content, MaskProfile.PLAIN)), 600,
                () -> "no translation; last error: " + Engine.get().service().stats().lastError());

        betterQuesting(engine);
        ftbQuests(engine);
        patchouli(engine);
    }

    // --------------------------------------------------------------------------------------- optional mods

    private static boolean optional(Engine engine, String modId, String probeClass) {
        boolean loaded = engine.isModLoaded(modId);
        boolean present = classExists(probeClass);
        REPORT.check(modId + ": detection agrees with classpath", loaded == present, "loaded=" + loaded + " classPresent=" + present);
        if (!loaded) {
            boolean none = true;
            for (String hook : engine.hooksSeen()) if (hook.startsWith(modId)) none = false;
            REPORT.check(modId + ": absent, game started and hooks skipped", none, "");
        }
        return loaded && present;
    }

    private static void betterQuesting(Engine engine) {
        if (!optional(engine, "betterquesting", "betterquesting.api2.utils.QuestTranslation")) return;
        try {
            final Method translate = Class.forName("betterquesting.api2.utils.QuestTranslation").getMethod("translate", String.class, Object[].class);
            final String text = "Collect twelve iron ingots from the deep cave";
            Object first = translate.invoke(null, text, new Object[0]);
            REPORT.check("betterquesting: original text is shown while translation is pending", text.equals(first), String.valueOf(first));
            REPORT.waitFor("betterquesting: quest text translated through QuestTranslation -> pipeline -> provider",
                    () -> ("TR| " + text).equals(call(translate, text)), 600, () -> String.valueOf(call(translate, text)));
            REPORT.check("betterquesting: language keys are left alone", "betterquesting.gui.name".equals(call(translate, "betterquesting.gui.name")),
                    String.valueOf(call(translate, "betterquesting.gui.name")));
        } catch (Throwable t) {
            REPORT.check("betterquesting: functional test setup", false, t.toString());
        }
    }

    private static void ftbQuests(Engine engine) {
        if (!optional(engine, "ftbquests", "com.feed_the_beast.ftbquests.client.FTBQuestsClient")) return;
        try {
            final Method convert = Class.forName("com.feed_the_beast.ftbquests.client.FTBQuestsClient").getMethod("addI18nAndColors", String.class);
            final String raw = "Collect &6Iron&r Ingots";
            Object first = convert.invoke(null, raw);
            REPORT.check("ftbquests: original text is shown while translation is pending",
                    String.valueOf(first).contains("Collect") && !String.valueOf(first).startsWith("TR|"), String.valueOf(first));
            REPORT.waitFor("ftbquests: text translated through FTB Quests -> pipeline -> provider, colour codes kept",
                    () -> {
                        String shown = String.valueOf(call(convert, raw));
                        return shown.startsWith("TR| Collect ") && shown.contains("§6Iron§r");
                    }, 600, () -> String.valueOf(call(convert, raw)));
        } catch (Throwable t) {
            REPORT.check("ftbquests: functional test setup", false, t.toString());
        }
    }

    private static void patchouli(Engine engine) {
        if (!optional(engine, "patchouli", "vazkii.patchouli.client.book.BookPage")) return;
        try {
            Class<?> bookClass = Class.forName("vazkii.patchouli.common.book.Book");
            Class<?> pageClass = Class.forName("vazkii.patchouli.client.book.page.PageText");
            final Object book = allocate(bookClass);
            set(bookClass, book, "i18n", Boolean.FALSE);
            final Object page = pageClass.newInstance();
            Field bookField = Class.forName("vazkii.patchouli.client.book.BookPage").getField("book");
            bookField.set(page, book);
            final Method i18n = Class.forName("vazkii.patchouli.client.book.BookPage").getMethod("i18n", String.class);
            Object first = i18n.invoke(page, "Iron Age");
            REPORT.check("patchouli: original label shown while translation is pending", "Iron Age".equals(first), String.valueOf(first));
            REPORT.waitFor("patchouli: page title translated through BookPage.i18n -> pipeline -> provider",
                    () -> "TR| Iron Age".equals(callOn(i18n, page, "Iron Age")), 600, () -> String.valueOf(callOn(i18n, page, "Iron Age")));
        } catch (Throwable t) {
            REPORT.check("patchouli: functional test setup", false, t.toString());
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static Object call(Method method, String text) {
        try {
            return method.getParameterTypes().length == 2 ? method.invoke(null, text, new Object[0]) : method.invoke(null, text);
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static Object callOn(Method method, Object target, String text) {
        try {
            return method.invoke(target, text);
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return allocate.invoke(unsafe, type);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getField(name);
        field.setAccessible(true);
        field.set(target, value);
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
        report.addProperty("betterquestingLoaded", engine.isModLoaded("betterquesting"));
        Path out = Paths.get(System.getProperty("autotranslation.smoketest.output", "smoke-result.json"));
        REPORT.write(out, report);
        if (server != null) server.close();
        CoreLog.info("[smoke] finished: " + (REPORT.allOk() ? "ALL CHECKS PASSED" : "FAILURES"));
        minecraft.shutdown();
    }
}
