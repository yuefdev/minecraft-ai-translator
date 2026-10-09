package me.langyue.autotranslation.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.architectury.event.events.client.ClientTickEvent;
import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.core.util.Secret;
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
    private static final String ORIGINAL_TITLE = "Collect &6Iron&r Ingots";

    private static final List<JsonObject> CHECKS = new ArrayList<>();
    private static int ticks = 0;
    private static boolean started = false;
    private static boolean finished = false;
    private static FakeOpenAiServer server;
    private static final List<Pending> WAITING = new ArrayList<>();

    /** A condition that is polled every client tick until it holds or its tick budget runs out. */
    private static final class Pending {
        final String name;
        final BooleanSupplier condition;
        int remaining;
        final Runnable onTimeout;

        Pending(String name, BooleanSupplier condition, int deadlineTicks, Runnable onTimeout) {
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
                if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null && ticks > 60) {
                    started = true;
                    begin(minecraft);
                } else if (ticks > 6000) {
                    check("reached title screen", false, "timeout waiting for the title screen");
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
        check("reached title screen", true, "");
        TranslationRuntime.service();
        check("translation runtime started", TranslationRuntime.service() != null, "");

        server = new FakeOpenAiServer();
        var settings = AutoTranslation.CONFIG.turkish;
        settings.enabled = true;
        settings.translateQuests = true;
        settings.translateBooks = true;
        settings.translateGeneric = true;
        settings.showOriginal = false;
        settings.endpoint = "http://127.0.0.1:" + server.port() + "/v1";
        settings.model = "smoke-model";
        settings.requestsPerMinute = 6000;
        AutoTranslation.CONFIG.translator = "OpenAI";
        minecraft.options.languageCode = "tr_tr";
        TranslationRuntime.setApiKey(Secret.of("sk-smoke-test-0123456789"));
        TranslationRuntime.applyConfig();
        check("provider configured against local fake endpoint", TranslationRuntime.providerConfigured(), "");

        checkMixinsApplied();
        checkOptionalMod("ftbquests", "dev.ftb.mods.ftbquests.quest.QuestObjectBase", SmokeTest::ftbQuestsFunctional);
        checkOptionalMod("patchouli", "vazkii.patchouli.client.book.BookPage", SmokeTest::patchouliFunctional);
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

    // ------------------------------------------------------------------------------------------ FTB Quests

    private static void ftbQuestsFunctional() {
        expectField("dev.ftb.mods.ftbquests.quest.QuestObjectBase", "at$title", "ftbquests: QuestObjectBase mixin applied");
        expectField("dev.ftb.mods.ftbquests.quest.Quest", "at$description", "ftbquests: Quest mixin applied");
        expectMethod("dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel$ChapterButton", "translateChapterSubtitle", "ftbquests: chapter button redirect applied");
        try {
            dev.ftb.mods.ftbquests.client.ClientQuestFile file = new dev.ftb.mods.ftbquests.client.ClientQuestFile();
            var group = new dev.ftb.mods.ftbquests.quest.ChapterGroup(0x10L, file);
            var chapter = new dev.ftb.mods.ftbquests.quest.Chapter(0x20L, file, group);
            chapter.setRawTitle("Getting Started");
            var quest = new dev.ftb.mods.ftbquests.quest.Quest(0x30L, chapter);
            quest.setRawTitle(ORIGINAL_TITLE);
            quest.setRawSubtitle("A short subtitle for this quest");
            quest.getRawDescription().add("Mine some iron ore and smelt it in a furnace.");
            quest.getRawDescription().add("{@pagebreak}");
            quest.getRawDescription().add("Bring 25 ingots back to the village.");
            String codeBefore = quest.getCodeString();
            Component first = quest.getTitle();
            check("ftbquests: original title is shown while translation is pending",
                    first.getString().contains("Collect") && !first.getString().startsWith("TR|"), first.getString());
            WAITING.add(new Pending("ftbquests: title translated through FTB Quests -> pipeline -> provider",
                    () -> quest.getTitle().getString().startsWith("TR| Collect Iron Ingots"), 600,
                    () -> check("ftbquests: title translated through FTB Quests -> pipeline -> provider", false,
                            "title stayed: " + quest.getTitle().getString() + " | last error: " + TranslationRuntime.service().stats().lastError())));
            WAITING.add(new Pending("ftbquests: description translated with page break and line count preserved",
                    () -> {
                        var lines = quest.getDescription();
                        return lines.size() == 3 && lines.get(0).getString().startsWith("TR| Mine some iron")
                                && lines.get(1).getString().equals("{@pagebreak}")
                                && lines.get(2).getString().startsWith("TR| Bring 25 ingots");
                    }, 600,
                    () -> check("ftbquests: description translated with page break and line count preserved", false,
                            quest.getDescription().stream().map(Component::getString).toList().toString())));
            WAITING.add(new Pending("ftbquests: subtitle translated",
                    () -> quest.getSubtitle().getString().startsWith("TR| A short subtitle"), 600,
                    () -> check("ftbquests: subtitle translated", false, quest.getSubtitle().getString())));
            WAITING.add(new Pending("ftbquests: data untouched (raw text, id, colour style)",
                    () -> {
                        boolean raw = quest.getRawTitle().equals(ORIGINAL_TITLE)
                                && quest.getRawSubtitle().equals("A short subtitle for this quest")
                                && quest.getRawDescription().equals(List.of("Mine some iron ore and smelt it in a furnace.", "{@pagebreak}", "Bring 25 ingots back to the village."))
                                && quest.getCodeString().equals(codeBefore);
                        boolean gold = quest.getTitle().getSiblings().stream().anyMatch(s ->
                                s.getString().equals("Iron") && s.getStyle().getColor() != null
                                        && s.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD)));
                        return raw && gold && quest.getTitle().getString().startsWith("TR|");
                    }, 600,
                    () -> check("ftbquests: data untouched (raw text, id, colour style)", false, quest.getTitle().toString())));
        } catch (Throwable t) {
            check("ftbquests: functional test setup", false, t.toString());
        }
    }

    // -------------------------------------------------------------------------------------------- Patchouli

    private static void patchouliFunctional() {
        expectMethod("vazkii.patchouli.client.book.BookEntry", "translateName", "patchouli: BookEntry.getName hooked");
        expectMethod("vazkii.patchouli.client.book.BookCategory", "translateName", "patchouli: BookCategory.getName hooked");
        expectMethod("vazkii.patchouli.client.book.BookPage", "translateLabel", "patchouli: BookPage.i18n hooked");
        expectMethod("vazkii.patchouli.client.book.gui.BookTextRenderer", "translateBeforeLayout", "patchouli: BookTextRenderer hooked before layout");
        try {
            var book = allocateBook();
            var page = new vazkii.patchouli.client.book.page.PageText();
            page.book = book;
            String before = page.i18n("Iron Age");
            check("patchouli: original label shown while translation is pending", before.equals("Iron Age"), before);
            WAITING.add(new Pending("patchouli: page title translated through BookPage.i18n -> pipeline -> provider",
                    () -> page.i18n("Iron Age").equals("TR| Iron Age"), 600,
                    () -> check("patchouli: page title translated through BookPage.i18n -> pipeline -> provider", false,
                            page.i18n("Iron Age") + " | last error: " + TranslationRuntime.service().stats().lastError())));
            String markup = "Smelt the ore in a $(item)furnace$(), then $(l:smoke:book/entry)read more$(/l).$(br)Done.";
            var original = Component.literal(markup);
            me.langyue.autotranslation.tr.patchouli.PatchouliSupport.translatePageText(book, original);
            WAITING.add(new Pending("patchouli: page markup commands preserved in the translation",
                    () -> {
                        Component translated = me.langyue.autotranslation.tr.patchouli.PatchouliSupport.translatePageText(book, original);
                        String text = translated.getString();
                        return text.startsWith("TR| ") && text.contains("$(item)furnace$()")
                                && text.contains("$(l:smoke:book/entry)read more$(/l)") && text.contains("$(br)");
                    }, 600,
                    () -> check("patchouli: page markup commands preserved in the translation", false,
                            me.langyue.autotranslation.tr.patchouli.PatchouliSupport.translatePageText(book, original).getString())));
        } catch (Throwable t) {
            check("patchouli: functional test setup", false, t.toString());
        }
    }

    /** A Book without running its heavy constructor; only the fields our code reads are filled in. */
    private static vazkii.patchouli.common.book.Book allocateBook() throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        var book = (vazkii.patchouli.common.book.Book) allocate.invoke(unsafe, vazkii.patchouli.common.book.Book.class);
        Field id = vazkii.patchouli.common.book.Book.class.getField("id");
        id.setAccessible(true);
        id.set(book, new net.minecraft.resources.ResourceLocation("smoke", "book"));
        return book;
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

    private static void expectField(String className, String field, String name) {
        try {
            Class.forName(className, false, SmokeTest.class.getClassLoader()).getDeclaredField(field);
            check(name, true, "");
        } catch (ReflectiveOperationException | LinkageError e) {
            check(name, false, e.toString());
        }
    }

    private static void expectMethod(String className, String fragment, String name) {
        try {
            Class<?> cls = Class.forName(className, false, SmokeTest.class.getClassLoader());
            boolean found = Arrays.stream(cls.getDeclaredMethods()).anyMatch(m -> m.getName().contains(fragment));
            check(name, found, found ? "" : "no merged handler containing '" + fragment + "' in " + className);
        } catch (ReflectiveOperationException | LinkageError e) {
            check(name, false, e.toString());
        }
    }

    private static void check(String name, boolean ok, String detail) {
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
