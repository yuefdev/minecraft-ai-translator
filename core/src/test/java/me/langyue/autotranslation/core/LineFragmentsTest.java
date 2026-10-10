package me.langyue.autotranslation.core;

import me.langyue.autotranslation.engine.LineFragments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineFragmentsTest {
    private static final String WHOLE = "Use RF energy stored in wireless batteries to create heat for 20 ticks";

    @Test
    void linesOfAWrappedTextAreFragmentsButTheWholeIsNot() {
        LineFragments fragments = new LineFragments(4);
        fragments.remember(WHOLE);
        assertFalse(fragments.isFragment(WHOLE), "the text itself is translated");
        assertTrue(fragments.isFragment("Use RF energy stored in"));
        assertTrue(fragments.isFragment("wireless batteries to create"));
        assertTrue(fragments.isFragment("create heat for 20 ticks"));
    }

    @Test
    void theColourCodeThatWrappingPutsInFrontIsIgnored() {
        LineFragments fragments = new LineFragments(4);
        fragments.remember(WHOLE);
        assertTrue(fragments.isFragment("§7batteries to create heat"));
        assertTrue(fragments.isFragment("§r§7batteries to create heat"));
    }

    @Test
    void oneWordLabelsAndUnrelatedTextsAreNeverFragments() {
        LineFragments fragments = new LineFragments(4);
        fragments.remember(WHOLE);
        assertFalse(fragments.isFragment("batteries"));
        assertFalse(fragments.isFragment("Heat"));
        assertFalse(fragments.isFragment("Iron Ingot"));
        assertFalse(fragments.isFragment(null));
        assertFalse(fragments.isFragment(""));
    }

    @Test
    void shortOrSingleWordTextsAreNotRemembered() {
        LineFragments fragments = new LineFragments(4);
        fragments.remember("Iron Ingot dust");
        fragments.remember("Supercalifragilisticexpialidocious");
        fragments.remember(null);
        assertFalse(fragments.isFragment("Iron Ingot"));
        assertFalse(fragments.isFragment("Supercalifragilistic expialidocious"));
    }

    @Test
    void theOldestTextIsForgottenFirst() {
        LineFragments fragments = new LineFragments(2);
        fragments.remember("first long text with several words");
        fragments.remember("second long text with several words");
        fragments.remember("third long text with several words");
        assertFalse(fragments.isFragment("first long text"));
        assertTrue(fragments.isFragment("second long text"));
        assertTrue(fragments.isFragment("third long text"));
    }
}
