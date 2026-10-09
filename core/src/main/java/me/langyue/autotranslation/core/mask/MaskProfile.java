package me.langyue.autotranslation.core.mask;

/**
 * Which kinds of non-translatable syntax can appear in a piece of text.
 */
public enum MaskProfile {
    /** Language-file style text: printf placeholders, {name}, section sign colour codes, URLs, ids, `code`. */
    PLAIN(false, false, "§"),
    /** FTB Quests rich text: additionally {@code &c} / {@code &#rrggbb} codes and escaped characters. */
    FTB_RICH(true, false, "[&{}\\\\§]"),
    /** Patchouli markup: additionally {@code $(...)} commands. */
    PATCHOULI(false, true, "\\$\\(|§"),
    /** Visible text of an already parsed component: structure (styles/events) lives outside the string. */
    COMPONENT(false, false, "");

    final boolean ampersandCodes;
    final boolean dollarCommands;
    /**
     * Characters/sequences that must not appear in the translatable (non-placeholder) part of an answer.
     * All real occurrences of the syntax are masked beforehand, so any such text in an answer is something the
     * model invented and would be interpreted as markup by the game (for example a stray {@code &} in FTB text).
     */
    final java.util.regex.Pattern forbiddenInVisibleText;

    MaskProfile(boolean ampersandCodes, boolean dollarCommands, String forbidden) {
        this.ampersandCodes = ampersandCodes;
        this.dollarCommands = dollarCommands;
        this.forbiddenInVisibleText = forbidden.isEmpty() ? null : java.util.regex.Pattern.compile(forbidden);
    }
}
