package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.provider.GoogleFreeProvider;
import me.langyue.autotranslation.core.provider.ProviderPreset;
import me.langyue.autotranslation.core.service.TranslationService;
import me.langyue.autotranslation.core.service.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.langyue.autotranslation.core.TestSupport.settings;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real network: sends typical modpack texts to the keyless Google service through the whole pipeline (masking,
 * batching, validation) and prints what comes back. Only runs with -Dautotranslation.live=true (CI job
 * "Live free services"), never in the normal build.
 */
@EnabledIfSystemProperty(named = "autotranslation.live", matches = "true")
class LiveFreeServicesTest {
    @TempDir
    Path dir;

    private static final String[][] SAMPLES = {
            {"ftbquests", "Collect 16 §6Iron Ingots§r and bring them back to the village."},
            {"ftbquests", "Craft your first pickaxe. You will need it to mine stone."},
            {"patchouli", "Smelt the ore in a $(item)Furnace$() to get ingots. See $(l:basics/smelting)smelting$() for more."},
            {"game", "Right-click to open the backpack"},
            {"game", "Energy: %s / %s FE"},
            {"game", "Shift-click to see more information"},
            {"betterquesting", "Kill the Ender Dragon and collect its egg."},
    };

    @Test
    void keylessGoogleTranslatesModpackTextIntoTurkish() throws Exception {
        TranslationCache cache = new TranslationCache(dir.resolve("cache.jsonl"), 1000);
        cache.load();
        CorrectionStore corrections = new CorrectionStore(dir.resolve("c.json"));
        corrections.load();
        String url = ProviderPreset.byId(ProviderPreset.GOOGLE_FREE).baseUrl();
        GoogleFreeProvider google = new GoogleFreeProvider(() -> url, () -> 30_000);
        TranslationService service = TranslationService.builder().settings(settings()).provider(() -> google)
                .cache(cache).corrections(corrections).coalesceShortTexts(true).build();
        try {
            List<CompletableFuture<Types.Result>> results = new ArrayList<>();
            for (int i = 0; i < SAMPLES.length; i++) {
                MaskProfile profile = SAMPLES[i][0].equals("patchouli") ? MaskProfile.PATCHOULI
                        : SAMPLES[i][0].equals("ftbquests") ? MaskProfile.FTB_RICH : MaskProfile.PLAIN;
                CompletableFuture<Types.Result> done = new CompletableFuture<>();
                Types.Result first = service.lookup(new Types.TextRequest(List.of(Types.Segment.plain(SAMPLES[i][1])),
                        new Types.Content(SAMPLES[i][0], "live check", "live:" + i, ""), profile, "live"), done::complete);
                if (first.state() != Types.State.PENDING) done.complete(first);
                results.add(done);
            }
            int translated = 0;
            for (int i = 0; i < SAMPLES.length; i++) {
                Types.Result r = results.get(i).get(60, TimeUnit.SECONDS);
                var t = r.segments().get(0).translation();
                System.out.println("LIVE | " + SAMPLES[i][1]);
                System.out.println("  -> | " + (t == null ? "(kept in English: " + r.detail() + ")" : t.text()));
                if (t != null) translated++;
            }
            System.out.println("LIVE | provider calls: " + service.stats().providerCalls() + ", last error: " + service.stats().lastError());
            assertTrue(translated > 0, "nothing came back from the keyless service: " + service.stats().lastError());
        } finally {
            service.close();
        }
    }
}
