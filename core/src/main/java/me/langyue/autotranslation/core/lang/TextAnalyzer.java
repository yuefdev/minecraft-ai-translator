package me.langyue.autotranslation.core.lang;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a piece of visible text still needs to be translated into the target language.
 *
 * <p>Turkish and English both use the Latin alphabet, so "does the text contain non-ASCII letters" is not a
 * usable test: plenty of Turkish text has no special character ("Bu gorev cok kolay"), and plenty of English
 * text borrows accented words. Instead the decision combines three kinds of evidence:
 * <ul>
 *   <li>Turkish-only letters (ğ ı ş İ are strong evidence, ç ö ü weaker),</li>
 *   <li>Turkish function words (matched ASCII-folded, so "icin" and "için" both count),</li>
 *   <li>English function words (the, and, to, ...), and an optional user supplied regex
 *       (the old {@code yourLanguageFeature} idea) which is treated as additional evidence only.</li>
 * </ul>
 */
public final class TextAnalyzer {

    public enum Decision {
        TRANSLATE,
        SKIP_BLANK,
        SKIP_NO_WORDS,
        SKIP_IDENTIFIER,
        SKIP_ALREADY_TARGET,
        SKIP_EXCLUDED
    }

    private static final Set<String> TURKISH_WORDS = Set.of(
            "ve", "bir", "bu", "icin", "ile", "ama", "gibi", "daha", "cok", "kadar", "sonra", "hic",
            "olarak", "olan", "yok", "degil", "veya", "bunu", "buna", "bunun", "size", "bana", "beni",
            "sana", "seni", "oyuncu", "gorev", "esya", "icinde", "uzerinde", "ayni", "yeni", "eski", "tum", "bazi",
            "sadece", "ancak", "fakat", "cunku", "diger", "iki", "ilk", "lutfen", "hadi", "evet", "hayir",
            "nasil", "neden", "nerede", "hangi", "baslat", "bitir", "tamamla", "odul", "bolum", "kitap",
            "sunucu", "oyun", "dunya", "blok", "alet", "silah", "zirh", "yemek"
    );

    private static final Set<String> ENGLISH_WORDS = Set.of(
            "the", "and", "to", "of", "a", "in", "is", "it", "you", "that", "for", "on", "with", "as", "are", "be",
            "this", "your", "will", "can", "have", "from", "or", "by", "at", "an", "not", "all", "but", "if", "when",
            "use", "get", "make", "each", "more", "new", "has", "was", "were", "which", "their", "them", "they",
            "then", "than", "into", "out", "up", "any", "only", "also", "just", "must", "should", "would", "could",
            "complete", "collect", "craft", "obtain", "place", "kill", "reward", "quest", "chapter", "click", "open"
    );

    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)?");
    private static final Pattern STRONG_TURKISH = Pattern.compile("[ğĞıİşŞ]");
    private static final Pattern WEAK_TURKISH = Pattern.compile("[çÇöÖüÜ]");
    private static final Pattern IDENTIFIER = Pattern.compile(
            "^(?:#?[a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_-]+(?:\\.[a-z0-9_-]+){2,}|[a-z0-9_./-]+\\.[a-z]{2,4})$");

    private final Pattern extraTargetFeature;
    private final Set<String> excludedWords;

    public TextAnalyzer(String extraTargetFeatureRegex, Set<String> excludedWords) {
        Pattern p = null;
        if (extraTargetFeatureRegex != null && !extraTargetFeatureRegex.isBlank()) {
            try {
                p = Pattern.compile(extraTargetFeatureRegex);
            } catch (RuntimeException ignored) {
                // a broken user regex must not disable translation; it simply contributes no evidence
            }
        }
        this.extraTargetFeature = p;
        this.excludedWords = excludedWords == null ? Set.of() : Set.copyOf(excludedWords);
    }

    public TextAnalyzer() {
        this(null, Set.of());
    }

    /**
     * @param visibleText text with formatting codes / placeholders already removed
     */
    public Decision analyze(String visibleText, LanguageProfile target) {
        if (visibleText == null || visibleText.isBlank()) return Decision.SKIP_BLANK;
        String text = visibleText.strip();
        if (IDENTIFIER.matcher(text).matches()) return Decision.SKIP_IDENTIFIER;

        int words = 0;
        int englishScore = 0;
        int turkishScore = 0;
        var matcher = WORD.matcher(text);
        int excluded = 0;
        while (matcher.find()) {
            String word = matcher.group();
            words++;
            String lower = word.toLowerCase(Locale.ROOT);
            if (excludedWords.contains(lower)) {
                excluded++;
                continue;
            }
            if (ENGLISH_WORDS.contains(lower)) englishScore += 2;
            String folded = fold(lower);
            if (TURKISH_WORDS.contains(folded)) turkishScore += 2;
            if (STRONG_TURKISH.matcher(word).find()) turkishScore += 4;
            else if (WEAK_TURKISH.matcher(word).find()) turkishScore += 2;
        }
        if (words == 0) return Decision.SKIP_NO_WORDS;
        if (excluded == words) return Decision.SKIP_EXCLUDED;
        if (extraTargetFeature != null && extraTargetFeature.matcher(text).find()) turkishScore += 2;

        if (target.isTurkish()) {
            if (turkishScore >= 3 && turkishScore > 2 * englishScore) return Decision.SKIP_ALREADY_TARGET;
        } else if (extraTargetFeature != null && extraTargetFeature.matcher(text).find() && englishScore == 0) {
            return Decision.SKIP_ALREADY_TARGET;
        }
        return Decision.TRANSLATE;
    }

    private static String fold(String lower) {
        String replaced = lower.replace('ı', 'i');
        String decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
