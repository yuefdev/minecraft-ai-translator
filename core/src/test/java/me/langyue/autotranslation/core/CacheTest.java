package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.cache.CacheKey;
import me.langyue.autotranslation.core.cache.CorrectionStore;
import me.langyue.autotranslation.core.cache.StoredTranslation;
import me.langyue.autotranslation.core.cache.TranslationCache;
import me.langyue.autotranslation.core.mask.RestoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {
    @TempDir
    Path dir;

    private static CacheKey key(String text, String lang, String content, String provider, String model, String glossary, String prompt) {
        return CacheKey.of(text, lang, content, provider, model, glossary, prompt);
    }

    private static final CacheKey BASE = key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1");

    @Test
    void everyKeyComponentChangesTheFingerprint() {
        String base = BASE.fingerprint();
        assertNotEquals(base, key("Collect gold", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1").fingerprint(), "source text");
        assertNotEquals(base, key("Collect iron", "de_de", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1").fingerprint(), "target language");
        assertNotEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:2", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1").fingerprint(), "content id");
        assertNotEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@other.example", "gpt-4o-mini", "g1", "p1").fingerprint(), "provider");
        assertNotEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o", "g1", "p1").fingerprint(), "model");
        assertNotEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g2", "p1").fingerprint(), "glossary version");
        assertNotEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p2").fingerprint(), "prompt version");
        assertEquals(base, key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1").fingerprint(), "same inputs");
    }

    @Test
    void fieldBoundariesCannotBeConfused() {
        CacheKey a = key("x", "tr_tr", "ab", "c", "m", "g", "p");
        CacheKey b = key("x", "tr_tr", "a", "bc", "m", "g", "p");
        assertNotEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void staleTranslationsAreNotReturnedWhenAnythingChanges() {
        TranslationCache cache = new TranslationCache(dir.resolve("cache.jsonl"), 1000);
        cache.put(BASE, StoredTranslation.plain("Demir topla"));
        assertEquals("Demir topla", cache.get(BASE).text());
        assertNull(cache.get(key("Collect iron!", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1")));
        assertNull(cache.get(key("Collect iron", "de_de", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1")));
        assertNull(cache.get(key("Collect iron", "tr_tr", "ftbquests:quest:9", "openai@api.openai.com", "gpt-4o-mini", "g1", "p1")));
        assertNull(cache.get(key("Collect iron", "tr_tr", "ftbquests:quest:1", "openai@api.openai.com", "gpt-4o-mini", "g2", "p1")));
    }

    @Test
    void cacheSurvivesRestartAndNeedsNoNetwork() {
        Path file = dir.resolve("cache.jsonl");
        TranslationCache first = new TranslationCache(file, 1000);
        first.put(BASE, StoredTranslation.plain("Demir topla"));

        TranslationCache reloaded = new TranslationCache(file, 1000);
        reloaded.load();
        assertEquals(1, reloaded.size());
        assertEquals("Demir topla", reloaded.get(BASE).text());
    }

    @Test
    void styledRunsRoundTripThroughDisk() {
        Path file = dir.resolve("cache.jsonl");
        TranslationCache first = new TranslationCache(file, 1000);
        StoredTranslation runs = new StoredTranslation("buraya tıkla",
                List.of(new RestoreResult.Run(1, "buraya "), new RestoreResult.Run(0, "tıkla")));
        first.put(BASE, runs);
        TranslationCache reloaded = new TranslationCache(file, 1000);
        reloaded.load();
        StoredTranslation back = reloaded.get(BASE);
        assertTrue(back.hasRuns());
        assertEquals(1, back.runs().get(0).styleIndex());
        assertEquals("tıkla", back.runs().get(1).text());
    }

    @Test
    void corruptLinesAreSkippedWithoutLosingTheRest() throws Exception {
        Path file = dir.resolve("cache.jsonl");
        TranslationCache first = new TranslationCache(file, 1000);
        first.put(BASE, StoredTranslation.plain("Demir topla"));
        Files.writeString(file, Files.readString(file) + "{\"k\":\"broken\n" + "not json at all\n",
                java.nio.charset.StandardCharsets.UTF_8);

        TranslationCache reloaded = new TranslationCache(file, 1000);
        reloaded.load();
        assertEquals("Demir topla", reloaded.get(BASE).text());
        assertTrue(reloaded.skippedCorruptLines() >= 1);
    }

    @Test
    void sizeIsBoundedAndOldestEntriesAreDroppedOnCompaction() throws Exception {
        Path file = dir.resolve("cache.jsonl");
        TranslationCache cache = new TranslationCache(file, 100);
        for (int i = 0; i < 160; i++) {
            cache.put(key("text " + i, "tr_tr", "c", "p", "m", "g", "p1"), StoredTranslation.plain("t" + i));
            Thread.sleep(1);
        }
        cache.compact();
        assertTrue(cache.size() <= 100, "size was " + cache.size());
        assertNotNull(cache.get(key("text 159", "tr_tr", "c", "p", "m", "g", "p1")), "newest entry must survive");
        assertNull(cache.get(key("text 0", "tr_tr", "c", "p", "m", "g", "p1")), "oldest entry must be dropped");
        TranslationCache reloaded = new TranslationCache(file, 100);
        reloaded.load();
        assertEquals(cache.size(), reloaded.size());
    }

    @Test
    void scopedCorrectionBeatsGlobalCorrection() throws Exception {
        CorrectionStore store = new CorrectionStore(dir.resolve("corrections.json"));
        store.put("Open the door", "Kapıyı aç", null);
        store.put("Open the door", "Kapıyı açın", "ftbquests:quest:7");
        assertEquals("Kapıyı açın", store.find("Open the door", "ftbquests:quest:7"));
        assertEquals("Kapıyı aç", store.find("Open the door", "ftbquests:quest:8"));
        assertEquals("Kapıyı aç", store.find("Open the door", null));
        assertNull(store.find("Close the door", null));
    }

    @Test
    void correctionsArePersistedAndRemovable() throws Exception {
        Path file = dir.resolve("corrections.json");
        CorrectionStore store = new CorrectionStore(file);
        store.put("Chest", "Sandık", null);
        CorrectionStore reloaded = new CorrectionStore(file);
        reloaded.load();
        assertEquals("Sandık", reloaded.find("Chest", null));
        reloaded.remove("Chest", null);
        CorrectionStore again = new CorrectionStore(file);
        again.load();
        assertNull(again.find("Chest", null));
    }
}
