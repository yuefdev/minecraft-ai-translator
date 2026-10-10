package me.langyue.autotranslation.engine;

/**
 * Remembers the texts that were just split into lines. Minecraft wraps a long text and then draws every line on its own;
 * the lines (and the pieces that wrapping hands to itself) must not be sent to the translator as texts of their own:
 * "and bugs in multiblocks." is not a sentence, and the whole text is translated anyway.
 *
 * <p>Only pieces of two words or more count as lines, so a one-word label is never mistaken for one.
 */
public final class LineFragments {
    private static final int MIN_WHOLE = 16;

    private final String[] recent;
    private int next;

    public LineFragments(int size) {
        this.recent = new String[Math.max(1, size)];
    }

    /** A text that is about to be wrapped or trimmed, and what it was replaced by (call for both). */
    public synchronized void remember(String whole) {
        if (whole == null || whole.length() < MIN_WHOLE || whole.indexOf(' ') < 0) return;
        for (String known : recent) {
            if (whole.equals(known)) return;
        }
        recent[next] = whole;
        next = (next + 1) % recent.length;
    }

    /** Is the text a piece (not the whole) of one of the remembered texts? */
    public synchronized boolean isFragment(String text) {
        if (text == null) return false;
        String core = withoutLeadingFormat(text);
        if (core.indexOf(' ') < 0) return false;
        for (String whole : recent) {
            if (whole != null && whole.length() > core.length() && whole.contains(core)) return true;
        }
        return false;
    }

    /** Wrapping puts the colour of the previous line in front of the next one: "§7" and the like. */
    private static String withoutLeadingFormat(String text) {
        int start = 0;
        while (start + 1 < text.length() && text.charAt(start) == '§') start += 2;
        return start == 0 ? text : text.substring(start);
    }
}
