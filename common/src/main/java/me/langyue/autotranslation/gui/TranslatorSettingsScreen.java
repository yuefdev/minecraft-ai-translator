package me.langyue.autotranslation.gui;

import me.langyue.autotranslation.AutoTranslation;
import me.langyue.autotranslation.config.Config;
import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.provider.ProviderException;
import me.langyue.autotranslation.core.provider.ProviderRequest;
import me.langyue.autotranslation.core.provider.TranslationContext;
import me.langyue.autotranslation.core.service.Types;
import me.langyue.autotranslation.core.util.Redactor;
import me.langyue.autotranslation.core.util.Secret;
import me.langyue.autotranslation.tr.TranslationRuntime;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Settings screen of the translator: switches, provider connection, editable glossary, local corrections and
 * the state of the translation queue. Built from vanilla widgets only; every text is a translation key, with a
 * Turkish language file shipped in the mod.
 */
public class TranslatorSettingsScreen extends Screen {
    private enum Tab {
        GENERAL("autotranslation.gui.tab.general"),
        PROVIDER("autotranslation.gui.tab.provider"),
        GLOSSARY("autotranslation.gui.tab.glossary"),
        CORRECTIONS("autotranslation.gui.tab.corrections"),
        STATUS("autotranslation.gui.tab.status");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final int CONTENT_WIDTH = 340;

    private final Screen parent;
    private Tab tab = Tab.GENERAL;
    private final Config.Turkish settings = AutoTranslation.CONFIG.turkish;

    // pending edits, applied by "Done"
    private boolean enabled = settings.enabled;
    private boolean quests = settings.translateQuests;
    private boolean books = settings.translateBooks;
    private boolean generic = settings.translateGeneric;
    private boolean allScreens = settings.translateAllScreens;
    private boolean showOriginal = settings.showOriginal;
    private boolean pauseEditing = settings.pauseWhileEditingQuests;
    private boolean bulk = settings.bulkLanguageFiles;
    private String endpoint = settings.endpoint;
    private String model = settings.model;
    private String newApiKey = "";
    private String requestsPerMinute = String.valueOf(settings.requestsPerMinute);
    private String timeoutSeconds = String.valueOf(settings.timeoutSeconds);
    private String maxRetries = String.valueOf(settings.maxRetries);

    // text of the add-row of the glossary / corrections tabs
    private String inputSource = "";
    private String inputTarget = "";
    private volatile String message = "";
    private volatile String testResult = "";
    private EntryList list;

    public TranslatorSettingsScreen(Screen parent) {
        super(Component.translatable("autotranslation.gui.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------------------------------------- layout

    @Override
    protected void init() {
        int left = (this.width - CONTENT_WIDTH) / 2;
        int tabWidth = CONTENT_WIDTH / Tab.values().length;
        int i = 0;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable(t.key), b -> {
                this.tab = t;
                this.message = "";
                this.rebuildWidgets();
            }).bounds(left + i * tabWidth, 22, tabWidth - 2, 20).build();
            button.active = t != tab;
            addRenderableWidget(button);
            i++;
        }
        this.list = null;
        switch (tab) {
            case GENERAL -> initGeneral(left);
            case PROVIDER -> initProvider(left);
            case GLOSSARY -> initPairs(left, true);
            case CORRECTIONS -> initPairs(left, false);
            case STATUS -> initStatus(left);
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(left + CONTENT_WIDTH / 2 + 2, this.height - 28, CONTENT_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left, this.height - 28, CONTENT_WIDTH / 2 - 2, 20).build());
    }

    private CycleButton<Boolean> toggle(int x, int y, String key, boolean initial, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(initial)
                .create(x, y, CONTENT_WIDTH, 20, Component.translatable(key), (b, value) -> setter.accept(value));
        addRenderableWidget(button);
        return button;
    }

    private EditBox field(int x, int y, int width, String value, int maxLength, Consumer<String> setter, String hintKey) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(setter);
        box.setHint(Component.translatable(hintKey));
        addRenderableWidget(box);
        return box;
    }

    private void initGeneral(int left) {
        int y = 54;
        toggle(left, y, "autotranslation.gui.enabled", enabled, v -> enabled = v);
        toggle(left, y += 24, "autotranslation.gui.quests", quests, v -> quests = v);
        toggle(left, y += 24, "autotranslation.gui.books", books, v -> books = v);
        toggle(left, y += 24, "autotranslation.gui.generic", generic, v -> generic = v);
        toggle(left, y += 24, "autotranslation.gui.all_screens", allScreens, v -> allScreens = v);
        toggle(left, y += 24, "autotranslation.gui.show_original", showOriginal, v -> showOriginal = v);
        toggle(left, y += 24, "autotranslation.gui.pause_editing", pauseEditing, v -> pauseEditing = v);
        toggle(left, y += 24, "autotranslation.gui.bulk", bulk, v -> bulk = v);
        addRenderableWidget(Button.builder(Component.translatable("autotranslation.gui.advanced"),
                        b -> this.minecraft.setScreen(AutoConfig.getConfigScreen(Config.class, this).get()))
                .bounds(left, y + 28, CONTENT_WIDTH, 20).build());
    }

    private void initProvider(int left) {
        int y = 58;
        int labelWidth = 110;
        int fieldX = left + labelWidth;
        int fieldWidth = CONTENT_WIDTH - labelWidth;
        field(fieldX, y, fieldWidth, endpoint, 300, v -> endpoint = v, "autotranslation.gui.endpoint");
        field(fieldX, y += 24, fieldWidth, model, 120, v -> model = v, "autotranslation.gui.model");
        EditBox key = field(fieldX, y += 24, fieldWidth, newApiKey, 300, v -> newApiKey = v, "autotranslation.gui.api_key_hint");
        key.setFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        boolean fromEnv = TranslationRuntime.secrets().isFromEnvironment();
        key.setEditable(!fromEnv);
        if (fromEnv) key.setHint(Component.translatable("autotranslation.gui.api_key_env"));
        else if (!TranslationRuntime.apiKey().isEmpty()) key.setHint(Component.translatable("autotranslation.gui.api_key_saved"));
        field(fieldX, y += 24, fieldWidth, requestsPerMinute, 5, v -> requestsPerMinute = v, "autotranslation.gui.rpm");
        field(fieldX, y += 24, fieldWidth, timeoutSeconds, 4, v -> timeoutSeconds = v, "autotranslation.gui.timeout");
        field(fieldX, y += 24, fieldWidth, maxRetries, 2, v -> maxRetries = v, "autotranslation.gui.retries");
        addRenderableWidget(Button.builder(Component.translatable("autotranslation.gui.test"), b -> runConnectionTest())
                .bounds(left, y + 28, CONTENT_WIDTH, 20).build());
    }

    private void initPairs(int left, boolean glossary) {
        int listTop = 50;
        int listBottom = this.height - 90;
        this.list = new EntryList(this.minecraft, CONTENT_WIDTH, this.height, listTop, listBottom, 18, left);
        if (glossary) {
            for (Map.Entry<String, String> e : TranslationRuntime.glossary().terms().entrySet()) {
                list.add(e.getKey(), e.getValue());
            }
        } else {
            for (CorrectionStore.Correction c : TranslationRuntime.corrections().all()) {
                if (c.scope() == null) list.add(c.source(), c.translation());
            }
        }
        addRenderableWidget(list);
        int y = this.height - 84;
        EditBox source = field(left, y, CONTENT_WIDTH, inputSource, 500, v -> inputSource = v,
                glossary ? "autotranslation.gui.glossary.source" : "autotranslation.gui.corrections.source");
        EditBox target = field(left, y + 22, CONTENT_WIDTH, inputTarget, 500, v -> inputTarget = v,
                glossary ? "autotranslation.gui.glossary.target" : "autotranslation.gui.corrections.translation");
        addRenderableWidget(Button.builder(Component.translatable("autotranslation.gui.add"),
                        b -> addPair(glossary)).bounds(left, y + 44, CONTENT_WIDTH / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("autotranslation.gui.remove"),
                        b -> removeSelected(glossary)).bounds(left + CONTENT_WIDTH / 2 + 2, y + 44, CONTENT_WIDTH / 2 - 2, 18).build());
    }

    private void initStatus(int left) {
        addRenderableWidget(Button.builder(Component.translatable("autotranslation.gui.retry_failed"), b -> {
            TranslationRuntime.service().clearFailures();
            TranslationRuntime.applyConfig();
            this.message = Component.translatable("autotranslation.gui.retry_started").getString();
        }).bounds(left, this.height - 54, CONTENT_WIDTH, 20).build());
    }

    // --------------------------------------------------------------------------------------------- actions

    private void addPair(boolean glossary) {
        String source = inputSource.strip();
        String target = inputTarget.strip();
        if (source.isEmpty() || target.isEmpty()) return;
        if (glossary) {
            TranslationRuntime.glossary().put(source, target);
            TranslationRuntime.saveGlossary();
        } else {
            String problem = TranslationRuntime.service().validateCorrection(source, target, MaskProfile.FTB_RICH);
            if (problem != null) {
                this.message = Component.translatable("autotranslation.gui.corrections.invalid", problem).getString();
                return;
            }
            TranslationRuntime.saveCorrection(source, target);
            TranslationRuntime.service().invalidateMemo();
            TranslationRuntime.onTranslationArrived();
        }
        inputSource = "";
        inputTarget = "";
        this.message = "";
        this.rebuildWidgets();
    }

    private void removeSelected(boolean glossary) {
        if (list == null || list.getSelected() == null) return;
        EntryList.Row row = list.getSelected();
        if (glossary) {
            TranslationRuntime.glossary().remove(row.source);
            TranslationRuntime.saveGlossary();
        } else {
            TranslationRuntime.removeCorrection(row.source);
            TranslationRuntime.service().invalidateMemo();
            TranslationRuntime.onTranslationArrived();
        }
        this.rebuildWidgets();
    }

    /** Sends one tiny request on a background thread; the result is shown, never the credentials. */
    private void runConnectionTest() {
        applyToConfig(false);
        testResult = Component.translatable("autotranslation.gui.test_running").getString();
        Thread thread = new Thread(() -> {
            long begin = System.currentTimeMillis();
            try {
                TranslationContext context = new TranslationContext("autotranslation", "connection test", "", "", "", Map.of());
                List<String> out = TranslationRuntime.provider().translate(
                        new ProviderRequest(LanguageProfile.of("tr_tr"), List.of("Hello"), context, false));
                testResult = Component.translatable("autotranslation.gui.test_ok", out.get(0),
                        System.currentTimeMillis() - begin).getString();
            } catch (ProviderException e) {
                testResult = Component.translatable("autotranslation.gui.test_fail", e.getMessage()).getString();
            } catch (RuntimeException e) {
                testResult = Component.translatable("autotranslation.gui.test_fail", Redactor.redact(e)).getString();
            }
        }, "AutoTranslation-ConnectionTest");
        thread.setDaemon(true);
        thread.start();
    }

    private static int parse(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.strip())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void applyToConfig(boolean save) {
        settings.enabled = enabled;
        settings.translateQuests = quests;
        settings.translateBooks = books;
        settings.translateGeneric = generic;
        settings.translateAllScreens = allScreens;
        settings.showOriginal = showOriginal;
        AutoTranslation.CONFIG.appendOriginal = showOriginal;
        settings.pauseWhileEditingQuests = pauseEditing;
        settings.bulkLanguageFiles = bulk;
        settings.endpoint = endpoint.strip();
        settings.model = model.strip();
        settings.requestsPerMinute = parse(requestsPerMinute, settings.requestsPerMinute, 1, 6000);
        settings.timeoutSeconds = parse(timeoutSeconds, settings.timeoutSeconds, 5, 300);
        settings.maxRetries = parse(maxRetries, settings.maxRetries, 0, 5);
        try {
            if (!newApiKey.isBlank() && !TranslationRuntime.secrets().isFromEnvironment()) {
                TranslationRuntime.setApiKey(Secret.of(newApiKey));
                newApiKey = "";
            }
        } catch (IOException e) {
            this.message = Redactor.redact(e);
        }
        if (save) AutoConfig.getConfigHolder(Config.class).save();
        TranslationRuntime.applyConfig();
    }

    private void saveAndClose() {
        applyToConfig(true);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ----------------------------------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        int left = (this.width - CONTENT_WIDTH) / 2;
        switch (tab) {
            case PROVIDER -> renderProviderLabels(graphics, left);
            case STATUS -> renderStatus(graphics, left);
            default -> {
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!message.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(message), this.width / 2, this.height - 44, 0xFF8080);
        }
    }

    private void renderProviderLabels(GuiGraphics graphics, int left) {
        String[] labels = {"endpoint", "model", "api_key", "rpm", "timeout", "retries"};
        int y = 63;
        for (String label : labels) {
            graphics.drawString(this.font, Component.translatable("autotranslation.gui." + label), left, y, 0xE0E0E0);
            y += 24;
        }
        if (!testResult.isEmpty()) {
            int maxWidth = CONTENT_WIDTH;
            int lineY = y + 54;
            for (FormattedCharSequence line : this.font.split(Component.literal(testResult), maxWidth)) {
                graphics.drawString(this.font, line, left, lineY, 0xC0C0C0);
                lineY += 10;
            }
        }
    }

    private void renderStatus(GuiGraphics graphics, int left) {
        Types.Stats stats = TranslationRuntime.service().stats();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(TranslationRuntime.providerConfigured()
                ? "autotranslation.gui.status.provider_ok" : "autotranslation.gui.status.provider_missing"));
        lines.add(Component.translatable("autotranslation.gui.status.queue", stats.queued(), stats.inFlight()));
        lines.add(Component.translatable("autotranslation.gui.status.results", stats.translatedSegments(), stats.failedSegments()));
        lines.add(Component.translatable("autotranslation.gui.status.cache", stats.cacheSize(), stats.cacheHits()));
        lines.add(Component.translatable("autotranslation.gui.status.calls", stats.providerCalls()));
        if (stats.pausedUntilMillis() > System.currentTimeMillis()) {
            long seconds = (stats.pausedUntilMillis() - System.currentTimeMillis()) / 1000;
            lines.add(Component.translatable("autotranslation.gui.status.paused", seconds));
        }
        Set<String> hooks = TranslationRuntime.hooksSeen();
        lines.add(Component.translatable("autotranslation.gui.status.ftb", integrationState("ftbquests", hooks)));
        lines.add(Component.translatable("autotranslation.gui.status.patchouli", integrationState("patchouli", hooks)));
        int y = 54;
        for (Component line : lines) {
            graphics.drawString(this.font, line, left, y, 0xE0E0E0);
            y += 12;
        }
        y += 4;
        Component error = stats.lastError().isEmpty()
                ? Component.translatable("autotranslation.gui.status.no_error")
                : Component.translatable("autotranslation.gui.status.last_error", stats.lastError());
        for (FormattedCharSequence line : this.font.split(error, CONTENT_WIDTH)) {
            graphics.drawString(this.font, line, left, y, stats.lastError().isEmpty() ? 0x80FF80 : 0xFF8080);
            y += 10;
        }
    }

    private static Component integrationState(String modId, Set<String> hooks) {
        if (!TranslationRuntime.isModLoaded(modId)) return Component.translatable("autotranslation.gui.status.not_installed");
        boolean used = hooks.stream().anyMatch(h -> h.startsWith(modId));
        return Component.translatable(used ? "autotranslation.gui.status.active" : "autotranslation.gui.status.waiting");
    }

    // ----------------------------------------------------------------------------------------------- list

    /** Scrollable "source -> translation" list. */
    private final class EntryList extends ObjectSelectionList<EntryList.Row> {
        private final int left;

        EntryList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight, int left) {
            super(minecraft, width, height, top, bottom, itemHeight);
            this.left = left;
            this.x0 = left;
            this.x1 = left + width;
            this.setRenderBackground(false);
        }

        void add(String source, String translation) {
            this.addEntry(new Row(source, translation));
        }

        @Override
        public int getRowWidth() {
            return CONTENT_WIDTH - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return left + CONTENT_WIDTH - 6;
        }

        private final class Row extends ObjectSelectionList.Entry<Row> {
            final String source;
            final String translation;

            Row(String source, String translation) {
                this.source = source;
                this.translation = translation;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                               int mouseY, boolean hovered, float partialTick) {
                String text = font.plainSubstrByWidth(source + "  →  " + translation, width - 8);
                graphics.drawString(font, text, left + 4, top + 4, 0xFFFFFF, false);
            }

            @Override
            public Component getNarration() {
                return Component.literal(source + " " + translation);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                EntryList.this.setSelected(this);
                inputSource = source;
                inputTarget = translation;
                return true;
            }
        }
    }
}
