package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.glossary.GameLanguageIndex;
import me.langyue.autotranslation.core.glossary.Glossary;
import me.langyue.autotranslation.core.glossary.GlossaryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlossaryTest {
    @TempDir
    Path dir;

    @Test
    void matchesWholeWordsCaseInsensitivelyLongestFirst() {
        Glossary g = Glossary.of(Map.of("Crafting Table", "Çalışma Masası", "Table", "Masa", "Chest", "Sandık"));
        Map<String, String> found = g.matching("Place the crafting table next to a chest. Chests are great.", 10);
        assertEquals("Çalışma Masası", found.get("Crafting Table"));
        assertEquals("Sandık", found.get("Chest"));
        assertEquals(java.util.List.of("Crafting Table", "Chest", "Table").stream().filter(found::containsKey).toList().size(), found.size());
        assertNull(g.matching("Anchestor", 10).get("Chest"), "must not match inside a longer word");
    }

    @Test
    void limitIsRespected() {
        Glossary g = Glossary.of(Map.of("a1", "x", "b1", "y", "c1", "z"));
        assertEquals(2, g.matching("a1 b1 c1", 2).size());
    }

    @Test
    void editingATermChangesTheVersionButHintsDoNot() {
        Glossary g = Glossary.of(Map.of("Chest", "Sandık"));
        String v1 = g.version();
        g.replaceHints(Map.of("Furnace", "Fırın"));
        assertEquals(v1, g.version(), "game language hints must not invalidate the cache");
        g.put("Chest", "Sandık!");
        assertNotEquals(v1, g.version());
        String v2 = g.version();
        g.remove("chest");
        assertNotEquals(v2, g.version());
    }

    @Test
    void identicalContentGivesIdenticalVersionRegardlessOfOrder() {
        Glossary a = Glossary.of(new java.util.LinkedHashMap<>(Map.of("A", "1", "B", "2")));
        Glossary b = new Glossary();
        b.put("B", "2");
        b.put("A", "1");
        assertEquals(a.version(), b.version());
    }

    @Test
    void userTermsOverrideHints() {
        Glossary g = new Glossary();
        g.replaceHints(Map.of("Furnace", "Ocak"));
        g.put("Furnace", "Fırın");
        assertEquals("Fırın", g.matching("A furnace", 5).get("Furnace"));
    }

    @Test
    void gameLanguageIndexUsesOfficialNamesAndDropsAmbiguousOnes() {
        GameLanguageIndex index = GameLanguageIndex.build(
                Map.of("block.minecraft.furnace", "Furnace",
                        "item.a.thing", "Thing", "item.b.thing", "Thing",
                        "gui.done", "Done", "random.key", "Hello"),
                Map.of("block.minecraft.furnace", "Fırın",
                        "item.a.thing", "Şey", "item.b.thing", "Nesne",
                        "gui.done", "Bitti", "random.key", "Merhaba"));
        assertEquals("Fırın", index.lookup("Furnace"));
        assertNull(index.lookup("Thing"), "ambiguous English text must not be resolved");
        assertEquals("Bitti", index.lookup("Done"));
        assertNull(index.lookup("Hello"), "only name-like keys are used");
    }

    @Test
    void storeCreatesStarterGlossaryAndReadsEdits() throws Exception {
        Path file = dir.resolve("glossary.json");
        GlossaryStore store = new GlossaryStore(file);
        Map<String, String> starter = store.load();
        assertTrue(Files.exists(file));
        assertEquals("Görev", starter.get("Quest"));
        store.save(Map.of("Quest", "Macera"));
        assertEquals("Macera", new GlossaryStore(file).load().get("Quest"));
    }

    @Test
    void brokenGlossaryFileFallsBackToStarter() throws Exception {
        Path file = dir.resolve("glossary.json");
        Files.writeString(file, "{ this is not json");
        assertEquals("Görev", new GlossaryStore(file).load().get("Quest"));
    }
}
