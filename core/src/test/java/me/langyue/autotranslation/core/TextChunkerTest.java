package me.langyue.autotranslation.core;

import me.langyue.autotranslation.core.service.TextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {
    private static String rejoin(List<TextChunker.Piece> pieces) {
        StringBuilder sb = new StringBuilder();
        for (TextChunker.Piece p : pieces) sb.append(p.text()).append(p.trailingWhitespace());
        return sb.toString();
    }

    @Test
    void shortTextIsOnePiece() {
        List<TextChunker.Piece> pieces = TextChunker.split("Hello world.", 3000);
        assertEquals(1, pieces.size());
        assertEquals("Hello world.", pieces.get(0).text());
    }

    @Test
    void longTextIsSplitLosslesslyAtSentenceBoundaries() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) sb.append("This is sentence number ").append(i).append(" of a long quest description. ");
        String text = sb.toString();
        List<TextChunker.Piece> pieces = TextChunker.split(text, 600);
        assertTrue(pieces.size() > 3);
        assertEquals(text, rejoin(pieces), "splitting must be lossless");
        for (TextChunker.Piece p : pieces) {
            assertTrue(p.text().length() + p.trailingWhitespace().length() <= 600);
            assertTrue(p.text().endsWith("."), "pieces should end at a sentence boundary: " + p.text());
        }
    }

    @Test
    void linesAreKeptTogetherWhenPossible() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("Line ").append(i).append(" with some words in it\n");
        List<TextChunker.Piece> pieces = TextChunker.split(sb.toString(), 400);
        assertEquals(sb.toString(), rejoin(pieces));
        for (TextChunker.Piece p : pieces) assertFalse(p.text().endsWith("with some wor"));
    }

    @Test
    void protectedTokensAreNeverCut() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append("[[P").append(i).append("]]");
        String text = sb.toString();
        List<TextChunker.Piece> pieces = TextChunker.split(text, 200);
        assertEquals(text, rejoin(pieces));
        for (TextChunker.Piece p : pieces) {
            long open = p.text().chars().filter(c -> c == '[').count();
            long close = p.text().chars().filter(c -> c == ']').count();
            assertEquals(open, close, "token cut in piece: " + p.text());
        }
    }

    @Test
    void textWithoutAnySpaceStillTerminates() {
        String text = "x".repeat(5000);
        List<TextChunker.Piece> pieces = TextChunker.split(text, 1000);
        assertEquals(text, rejoin(pieces));
        assertTrue(pieces.size() >= 5);
    }
}
