package me.langyue.autotranslation.core.mask;

/**
 * Which kinds of non-translatable syntax can appear in a piece of text.
 */
public enum MaskProfile {
    /** Language-file style text: printf placeholders, {name}, section sign colour codes, URLs, ids, `code`. */
    PLAIN(false, false),
    /** FTB Quests rich text: additionally {@code &c} / {@code &#rrggbb} codes and escaped characters. */
    FTB_RICH(true, false),
    /** Patchouli markup: additionally {@code $(...)} commands. */
    PATCHOULI(false, true),
    /** Visible text of an already parsed component: structure (styles/events) lives outside the string. */
    COMPONENT(false, false);

    final boolean ampersandCodes;
    final boolean dollarCommands;

    MaskProfile(boolean ampersandCodes, boolean dollarCommands) {
        this.ampersandCodes = ampersandCodes;
        this.dollarCommands = dollarCommands;
    }
}
