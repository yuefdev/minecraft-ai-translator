package me.langyue.autotranslation.core.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long masked text into pieces of at most {@code maxChars} so that each piece fits in one provider
 * call while keeping the meaning of every piece intact: it cuts at line breaks first, then at sentence ends,
 * then at spaces, and never inside a protected token (tokens contain no whitespace).
 */
public final class TextChunker {

    /** A piece of text plus the whitespace that followed it in the source (re-added after translation). */
    public static final class Piece {
        private final String text;
        private final String trailingWhitespace;

        public Piece(String text, String trailingWhitespace) {
            this.text = text;
            this.trailingWhitespace = trailingWhitespace;
        }

        public String text() {
            return text;
        }

        public String trailingWhitespace() {
            return trailingWhitespace;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Piece other = (Piece) o;
            return java.util.Objects.equals(text, other.text) && java.util.Objects.equals(trailingWhitespace, other.trailingWhitespace);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(text, trailingWhitespace);
        }

        @Override
        public String toString() {
            return "Piece[" + "text=" + text + ", " + "trailingWhitespace=" + trailingWhitespace + "]";
        }
    }

    private TextChunker() {
    }

    public static List<Piece> split(String text, int maxChars) {
        List<Piece> pieces = new ArrayList<>();
        int max = Math.max(200, maxChars);
        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(length, start + max);
            if (end < length) {
                end = findCut(text, start, end);
            }
            int textEnd = end;
            while (textEnd > start && Character.isWhitespace(text.charAt(textEnd - 1))) textEnd--;
            if (textEnd == start) {
                // only whitespace left in this window; attach it to the previous piece
                if (!pieces.isEmpty()) {
                    Piece last = pieces.remove(pieces.size() - 1);
                    pieces.add(new Piece(last.text(), last.trailingWhitespace() + text.substring(start, end)));
                } else {
                    pieces.add(new Piece("", text.substring(start, end)));
                }
            } else {
                pieces.add(new Piece(text.substring(start, textEnd), text.substring(textEnd, end)));
            }
            start = end;
        }
        if (pieces.isEmpty()) pieces.add(new Piece("", ""));
        return pieces;
    }

    /** Index just after the preferred cut point inside (start, limit]. */
    private static int findCut(String text, int start, int limit) {
        int min = start + (limit - start) / 3;
        for (int i = limit; i > min; i--) {
            if (text.charAt(i - 1) == '\n') return i;
        }
        for (int i = limit; i > min; i--) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c) && i - 2 >= start) {
                char before = text.charAt(i - 2);
                if (before == '.' || before == '!' || before == '?' || before == '。') return i;
            }
        }
        for (int i = limit; i > min; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) return i;
        }
        // No whitespace at all (e.g. one very long token run): cut at the limit but not inside a [[...]] token.
        int open = text.lastIndexOf("[[", limit - 1);
        int close = text.indexOf("]]", Math.max(open, start));
        if (open >= start && open < limit && (close < 0 || close + 2 > limit) && open > min) {
            return open;
        }
        return limit;
    }
}
