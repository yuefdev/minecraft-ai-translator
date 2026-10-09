package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.lang.LanguageProfile;
import me.langyue.autotranslation.core.lang.TextAnalyzer;
import me.langyue.autotranslation.core.lang.TextAnalyzer.Decision;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextAnalyzerTest {
    private final TextAnalyzer analyzer = new TextAnalyzer();
    private final LanguageProfile tr = LanguageProfile.TURKISH;

    @Test
    void englishNeedsTranslation() {
        assertEquals(Decision.TRANSLATE, analyzer.analyze("Collect 5 iron ingots to continue", tr));
        assertEquals(Decision.TRANSLATE, analyzer.analyze("Stone", tr));
        assertEquals(Decision.TRANSLATE, analyzer.analyze("The quest is complete", tr));
    }

    @Test
    void turkishWithSpecialCharactersIsLeftAlone() {
        assertEquals(Decision.SKIP_ALREADY_TARGET, analyzer.analyze("Bu görev çok kolay", tr));
        assertEquals(Decision.SKIP_ALREADY_TARGET, analyzer.analyze("Başla", tr));
    }

    @Test
    void turkishWithoutSpecialCharactersIsAlsoRecognised() {
        // special characters alone are not enough: many Turkish texts are typed without them
        assertEquals(Decision.SKIP_ALREADY_TARGET, analyzer.analyze("Bu gorev cok kolay ve ilk odul sizin", tr));
        assertEquals(Decision.SKIP_ALREADY_TARGET, analyzer.analyze("Yeni bolum icin hadi baslat", tr));
    }

    @Test
    void mixedTextWithEnglishMajorityStillTranslates() {
        assertEquals(Decision.TRANSLATE, analyzer.analyze("Craft the Çiğ Köfte", tr));
    }

    @Test
    void identifiersAndBlankTextAreSkipped() {
        assertEquals(Decision.SKIP_IDENTIFIER, analyzer.analyze("minecraft:stone", tr));
        assertEquals(Decision.SKIP_IDENTIFIER, analyzer.analyze("item.minecraft.stone", tr));
        assertEquals(Decision.SKIP_BLANK, analyzer.analyze("   ", tr));
        assertEquals(Decision.SKIP_NO_WORDS, analyzer.analyze("12 / 34", tr));
    }

    @Test
    void excludedWordsAreNotTranslated() {
        TextAnalyzer withExclusions = new TextAnalyzer(null, Set.of("minecraft", "forge"));
        assertEquals(Decision.SKIP_EXCLUDED, withExclusions.analyze("Minecraft Forge", tr));
        assertEquals(Decision.TRANSLATE, withExclusions.analyze("Install Forge to continue", tr));
    }

    @Test
    void userSuppliedFeatureRegexIsOnlyExtraEvidence() {
        // a script-specific regex (the old yourLanguageFeature) must not make English text look Turkish
        TextAnalyzer withRegex = new TextAnalyzer("[çğıöşüÇĞİÖŞÜ]", Set.of());
        assertEquals(Decision.TRANSLATE, withRegex.analyze("Open the door", tr));
    }

    @Test
    void brokenRegexDoesNotDisableTranslation() {
        TextAnalyzer broken = new TextAnalyzer("([", Set.of());
        assertEquals(Decision.TRANSLATE, broken.analyze("Open the door", tr));
    }

    @Test
    void minecraftCodeMapsToProviderCode() {
        assertEquals("tr", LanguageProfile.of("tr_tr").providerCode());
        assertEquals("tr", LanguageProfile.of("TR_TR").providerCode());
        assertEquals("Turkish", LanguageProfile.of("tr_tr").englishName());
        assertEquals("zh-CN", LanguageProfile.of("zh_cn").providerCode());
        assertEquals("de", LanguageProfile.of("de_de").providerCode());
    }
}
