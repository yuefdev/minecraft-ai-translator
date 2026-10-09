package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.mask.MaskProfile;
import me.langyue.autotranslation.core.mask.MaskedText;
import me.langyue.autotranslation.core.mask.Masker;
import me.langyue.autotranslation.core.mask.RestoreResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaskerTest {
    private final Masker masker = new Masker();

    private String roundTrip(String source, MaskProfile profile) {
        MaskedText masked = masker.mask(source, profile);
        // an "identity translation" must restore byte for byte
        RestoreResult r = masker.restore(masked, masked.masked());
        assertTrue(r.ok(), "identity must be accepted for: " + source + " (" + r.reason() + ")");
        return r.text();
    }

    @Test
    void printfPlaceholdersAreProtected() {
        MaskedText m = masker.mask("Collect %d of %s (%1$s) 100%% done, %.2f", MaskProfile.PLAIN);
        assertEquals(List.of("%d", "%s", "%1$s", "%%", "%.2f"), m.placeholders());
        assertFalse(m.masked().contains("%"));
    }

    @Test
    void percentSignInProseIsNotAPlaceholder() {
        MaskedText m = masker.mask("Deals 50% more damage", MaskProfile.PLAIN);
        assertTrue(m.placeholders().isEmpty());
    }

    @Test
    void namedPlaceholdersColorCodesUrlsAndIdsAreProtected() {
        String source = "§aHello {player}! Visit https://example.com/a?b=1. Use minecraft:iron_ingot or #forge:ingots/iron.";
        MaskedText m = masker.mask(source, MaskProfile.PLAIN);
        assertTrue(m.placeholders().contains("§a"));
        assertTrue(m.placeholders().contains("{player}"));
        assertTrue(m.placeholders().contains("https://example.com/a?b=1"));
        assertTrue(m.placeholders().contains("minecraft:iron_ingot"));
        assertTrue(m.placeholders().contains("#forge:ingots/iron"));
        assertEquals(source, roundTrip(source, MaskProfile.PLAIN));
    }

    @Test
    void ftbRichTextCodesAndSubstitutesAreProtected() {
        String source = "&6Gold &#ff8800hex&r text\\nnext {image:ftbquests:textures/x.png width:100 text:\"Hi there\"} done \\& end";
        MaskedText m = masker.mask(source, MaskProfile.FTB_RICH);
        assertTrue(m.placeholders().contains("&6"));
        assertTrue(m.placeholders().contains("&#ff8800"));
        assertTrue(m.placeholders().contains("&r"));
        assertTrue(m.placeholders().contains("\\n"));
        assertTrue(m.placeholders().contains("{image:ftbquests:textures/x.png width:100 text:\"Hi there\"}"));
        assertTrue(m.placeholders().contains("\\&"));
        assertEquals(source, roundTrip(source, MaskProfile.FTB_RICH));
    }

    @Test
    void ampersandIsPlainTextOutsideFtbRichText() {
        MaskedText m = masker.mask("Fire & Ice", MaskProfile.PLAIN);
        assertTrue(m.placeholders().isEmpty());
    }

    @Test
    void patchouliCommandsAreProtectedButLinkTextStaysTranslatable() {
        String source = "Use $(l:patchouli:basics/intro)the intro page$(/l) and $(item)Iron$(), see $(k:key.use).$(br)Next.";
        MaskedText m = masker.mask(source, MaskProfile.PATCHOULI);
        assertEquals(List.of("$(l:patchouli:basics/intro)", "$(/l)", "$(item)", "$()", "$(k:key.use)", "$(br)"), m.placeholders());
        assertTrue(m.masked().contains("the intro page"));
        assertEquals(source, roundTrip(source, MaskProfile.PATCHOULI));
    }

    @Test
    void slashCommandsAndBackticksAreProtected() {
        MaskedText m = masker.mask("Run /give @p minecraft:diamond 5 and `/home base` now", MaskProfile.PLAIN);
        assertTrue(m.placeholders().contains("/give @p minecraft:diamond 5"));
        assertTrue(m.placeholders().contains("`/home base`"));
        assertTrue(m.masked().contains(" and "));
    }

    @Test
    void realTranslationKeepsEverythingAndRestoresOriginals() {
        MaskedText m = masker.mask("Collect %d iron ingots for {npc}", MaskProfile.PLAIN);
        RestoreResult r = masker.restore(m, "{{ignored}} [[P1]] için [[P0]] demir külçesi topla");
        // token spelling variations are tolerated, but this answer invents text around valid tokens
        assertTrue(r.ok(), r.reason());
        assertEquals("{{ignored}} {npc} için %d demir külçesi topla", r.text());
    }

    @Test
    void spacedAndRecasedTokensAreNormalised() {
        MaskedText m = masker.mask("Give %s now", MaskProfile.PLAIN);
        RestoreResult r = masker.restore(m, "Şimdi [[ P0 ]] ver");
        assertTrue(r.ok(), r.reason());
        assertEquals("Şimdi %s ver", r.text());
    }

    @Test
    void lostDuplicatedAndInventedPlaceholdersAreRejected() {
        MaskedText m = masker.mask("Give %s and %d", MaskProfile.PLAIN);
        assertFalse(masker.restore(m, "Ver [[P0]]").ok());
        assertFalse(masker.restore(m, "Ver [[P0]] [[P0]] [[P1]]").ok());
        assertFalse(masker.restore(m, "Ver [[P0]] [[P1]] [[P2]]").ok());
        assertTrue(masker.restore(m, "Ver [[P1]] ve [[P0]]").ok());
    }

    @Test
    void changedNumbersAreRejected() {
        MaskedText m = masker.mask("Kill 25 zombies within 3 minutes", MaskProfile.PLAIN);
        assertFalse(masker.restore(m, "3 dakika içinde 52 zombi öldür").ok());
        assertFalse(masker.restore(m, "Üç dakika içinde 25 zombi öldür").ok());
        assertTrue(masker.restore(m, "3 dakika içinde 25 zombi öldür").ok());
    }

    @Test
    void numberFormatChangesWithSameDigitsAreAccepted() {
        MaskedText m = masker.mask("Earn 1,000 coins and 2.5 xp", MaskProfile.PLAIN);
        assertTrue(masker.restore(m, "1.000 jeton ve 2,5 xp kazan").ok());
    }

    @Test
    void changedLineStructureIsRejected() {
        MaskedText m = masker.mask("First line\nSecond line", MaskProfile.PLAIN);
        assertFalse(masker.restore(m, "Birinci satır İkinci satır").ok());
        assertTrue(masker.restore(m, "Birinci satır\nİkinci satır").ok());
    }

    @Test
    void chattyAnswersAreRejected() {
        MaskedText m = masker.mask("Open the chest to continue.", MaskProfile.PLAIN);
        assertFalse(masker.restore(m, "Elbette! İşte çeviri: Devam etmek için sandığı aç.").ok());
        assertFalse(masker.restore(m, "Sure, here it is: Devam etmek için sandığı aç.").ok());
    }

    @Test
    void emptyAnswerIsRejected() {
        MaskedText m = masker.mask("Hello there", MaskProfile.PLAIN);
        assertFalse(masker.restore(m, "   ").ok());
        assertFalse(masker.restore(m, null).ok());
    }

    @Test
    void leadingAndTrailingSpacesOfTheSourceArePreserved() {
        MaskedText m = masker.mask("  Indented text ", MaskProfile.PLAIN);
        RestoreResult r = masker.restore(m, "Girintili metin");
        assertTrue(r.ok());
        assertEquals("  Girintili metin ", r.text());
    }

    @Test
    void sourceThatLooksLikeTokensIsNeverSent() {
        MaskedText m = masker.mask("Weird [[P0]] text", MaskProfile.PLAIN);
        assertTrue(m.unsafe());
        assertFalse(masker.restore(m, "Tuhaf [[P0]] metin").ok());
    }

    @Test
    void textWithoutLettersHasNothingToTranslate() {
        assertTrue(masker.hasNoTranslatableLetters(masker.mask("%d / %d", MaskProfile.PLAIN)));
        assertTrue(masker.hasNoTranslatableLetters(masker.mask("&6{key}§r", MaskProfile.FTB_RICH)));
        assertFalse(masker.hasNoTranslatableLetters(masker.mask("Hello %s", MaskProfile.PLAIN)));
    }

    // ----- styled runs (components) ---------------------------------------------------------------------

    @Test
    void runsKeepTheirStyleIndexWhenTheTranslationReordersThem() {
        MaskedText m = masker.maskRuns(List.of("Click ", "here", " to continue"), MaskProfile.COMPONENT);
        assertEquals(3, m.runCount());
        RestoreResult r = masker.restore(m, "[[R2]]Devam etmek için[[/R2]] [[R1]]buraya[[/R1]] [[R0]]tıkla[[/R0]]");
        assertTrue(r.ok(), r.reason());
        assertEquals(3, r.runs().size());
        assertEquals(2, r.runs().get(0).styleIndex());
        assertEquals(1, r.runs().get(1).styleIndex());
        assertEquals(0, r.runs().get(2).styleIndex());
        assertEquals("buraya ", r.runs().get(1).text());
    }

    @Test
    void runsWithPlaceholdersRestoreThem() {
        MaskedText m = masker.maskRuns(List.of("You have ", "%d", " lives"), MaskProfile.COMPONENT);
        RestoreResult r = masker.restore(m, "[[R0]]Sahip olduğun[[/R0]] [[R1]][[P0]][[/R1]] [[R2]]can[[/R2]]");
        // %d is its own run here (a run consisting only of a placeholder)
        assertTrue(r.ok(), r.reason());
        assertEquals("%d", r.runs().get(1).text().strip());
    }

    @Test
    void brokenRunStructureIsRejected() {
        MaskedText m = masker.maskRuns(List.of("Hello ", "world"), MaskProfile.COMPONENT);
        assertFalse(masker.restore(m, "[[R0]]Merhaba [[/R0]]").ok(), "missing run");
        assertFalse(masker.restore(m, "[[R0]]Merhaba [[R1]]dünya[[/R1]][[/R0]]").ok(), "nested runs");
        assertFalse(masker.restore(m, "[[R0]]Merhaba[[/R0]][[R0]]x[[/R0]][[R1]]dünya[[/R1]]").ok(), "duplicate run");
        assertFalse(masker.restore(m, "[[R0]]Merhaba[[/R1]][[R1]]dünya[[/R0]]").ok(), "unbalanced tags");
    }

    @Test
    void sliceRenumbersTokensForIndependentPieces() {
        MaskedText whole = masker.mask("First %s sentence. Second {x} sentence with %d.", MaskProfile.PLAIN);
        String masked = whole.masked();
        int cut = masked.indexOf("Second");
        MaskedText second = masker.slice(whole, masked.substring(cut));
        assertEquals(List.of("{x}", "%d"), second.placeholders());
        assertTrue(second.masked().contains("[[P0]]") && second.masked().contains("[[P1]]"));
        assertEquals("Second {x} sentence with %d.", second.original());
    }
}
