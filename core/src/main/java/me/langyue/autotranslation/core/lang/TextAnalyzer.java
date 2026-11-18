package me.langyue.autotranslation.core.lang;

import me.langyue.autotranslation.core.util.Compat;
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

    private static final Set<String> TURKISH_WORDS = Compat.setOf(
            "ve", "bir", "bu", "icin", "ile", "ama", "gibi", "daha", "cok", "kadar", "sonra", "hic",
            "olarak", "olan", "yok", "degil", "veya", "bunu", "buna", "bunun", "size", "bana", "beni",
            "sana", "seni", "oyuncu", "gorev", "esya", "icinde", "uzerinde", "ayni", "yeni", "eski", "tum", "bazi",
            "sadece", "ancak", "fakat", "cunku", "diger", "iki", "ilk", "lutfen", "hadi", "evet", "hayir",
            "nasil", "neden", "nerede", "hangi", "baslat", "bitir", "tamamla", "odul", "bolum", "kitap",
            "sunucu", "oyun", "dunya", "blok", "alet", "silah", "zirh", "yemek"
    );

    private static final Set<String> ENGLISH_WORDS = Compat.setOf(
            "the", "and", "to", "of", "a", "in", "is", "it", "you", "that", "for", "on", "with", "as", "are", "be",
            "this", "your", "will", "can", "have", "from", "or", "by", "at", "an", "not", "all", "but", "if", "when",
            "use", "get", "make", "each", "more", "new", "has", "was", "were", "which", "their", "them", "they",
            "then", "than", "into", "out", "up", "any", "only", "also", "just", "must", "should", "would", "could",
            "complete", "collect", "craft", "obtain", "place", "kill", "reward", "quest", "chapter", "click", "open"
    );

    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)?");
    private static final Pattern STRONG_TURKISH = Pattern.compile("[ğĞıİşŞ]");
    private static final Pattern WEAK_TURKISH = Pattern.compile("[çÇöÖüÜ]");

    private static final Pattern CYRILLIC = Pattern.compile("[\\u0400-\\u04FF]");
    private static final Pattern HAN = Pattern.compile("[\\u4E00-\\u9FFF]");
    private static final Pattern JA_SCRIPT = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FFF]");
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7AF\\u1100-\\u11FF]");
    private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");
    private static final Pattern HEBREW = Pattern.compile("[\\u0590-\\u05FF]");
    private static final Pattern GREEK = Pattern.compile("[\\u0370-\\u03FF]");
    private static final Pattern THAI = Pattern.compile("[\\u0E00-\\u0E7F]");

    private static final Set<String> FRENCH_WORDS = Compat.setOf(
            "les", "des", "une", "dans", "pour", "avec", "sur", "pas", "plus", "vous", "nous",
            "mais", "aux", "cette", "sont", "est", "etre", "avoir", "faire", "tout", "comme", "votre", "leurs",
            "et", "du", "au", "qui", "que", "ces", "besoin", "de"
    );
    private static final Pattern FRENCH_ACCENTS = Pattern.compile("[éèêëàâùûîïôœæÉÈÊËÀÂÙÛÎÏÔŒÆ]");

    private static final Set<String> GERMAN_WORDS = Compat.setOf(
            "und", "der", "die", "das", "nicht", "mit", "auf", "fur", "ein", "eine", "einer", "einem", "einen",
            "oder", "auch", "sind", "ist", "wird", "kann", "nach", "aus", "dem", "den", "von", "vom", "zu", "zum", "zur", "bei", "im", "am"
    );
    private static final Pattern GERMAN_ACCENTS = Pattern.compile("[äöüßÄÖÜ]");

    private static final Set<String> SPANISH_WORDS = Compat.setOf(
            "los", "las", "una", "del", "para", "con", "por", "como", "pero", "mas", "esta",
            "este", "son", "todo", "puede", "sus", "sin", "de", "en", "que"
    );
    private static final Pattern SPANISH_ACCENTS = Pattern.compile("[áéíóúñ¿¡ÁÉÍÓÚÑ]");

    private static final Set<String> PORTUGUESE_WORDS = Compat.setOf(
            "uma", "dos", "das", "para", "com", "por", "como", "mais", "nao", "esta", "sao",
            "voce", "seus", "pelo", "de", "em", "que"
    );
    private static final Pattern PORTUGUESE_ACCENTS = Pattern.compile("[ãõáéíóúâêôÃÕÁÉÍÓÚÂÊÔ]");

    private static final Set<String> ITALIAN_WORDS = Compat.setOf(
            "gli", "una", "nel", "della", "delle", "degli", "con", "per", "come", "sono",
            "puo", "questo", "questa", "anche", "alla", "di", "che", "ed"
    );
    private static final Pattern ITALIAN_ACCENTS = Pattern.compile("[àèéìòùÀÈÉÌÒÙ]");

    private static final Pattern IDENTIFIER = Pattern.compile(
            "^(?:#?[a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_-]+(?:\\.[a-z0-9_-]+){2,}|[a-z0-9_./-]+\\.[a-z]{2,4})$");

    private final Pattern extraTargetFeature;
    private final Set<String> excludedWords;

    public TextAnalyzer(String extraTargetFeatureRegex, Set<String> excludedWords) {
        Pattern p = null;
        if (extraTargetFeatureRegex != null && !Compat.isBlank(extraTargetFeatureRegex)) {
            try {
                p = Pattern.compile(extraTargetFeatureRegex);
            } catch (RuntimeException ignored) {
                // a broken user regex must not disable translation; it simply contributes no evidence
            }
        }
        this.extraTargetFeature = p;
        this.excludedWords = excludedWords == null ? Compat.setOf() : Compat.setCopyOf(excludedWords);
    }

    public TextAnalyzer() {
        this(null, Compat.setOf());
    }

    /**
     * @param visibleText text with formatting codes / placeholders already removed
     */
    public Decision analyze(String visibleText, LanguageProfile target) {
        if (visibleText == null || Compat.isBlank(visibleText)) return Decision.SKIP_BLANK;
        String text = Compat.strip(visibleText);
        if (IDENTIFIER.matcher(text).matches()) return Decision.SKIP_IDENTIFIER;

        int words = 0;
        int englishScore = 0;
        int turkishScore = 0;
        java.util.List<String> nonExcludedWords = target.isTurkish() ? null : new java.util.ArrayList<String>();
        java.util.regex.Matcher matcher = WORD.matcher(text);
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
            if (nonExcludedWords != null) nonExcludedWords.add(word);
        }
        if (words == 0) return Decision.SKIP_NO_WORDS;
        if (excluded == words) return Decision.SKIP_EXCLUDED;
        if (extraTargetFeature != null && extraTargetFeature.matcher(text).find()) turkishScore += 2;

        if (target.isTurkish()) {
            if (turkishScore >= 3 && turkishScore > 2 * englishScore) return Decision.SKIP_ALREADY_TARGET;
            return Decision.TRANSLATE;
        }
        return checkNonTurkishTarget(text, target, nonExcludedWords, englishScore);
    }

    private Decision checkNonTurkishTarget(String text, LanguageProfile target, java.util.List<String> words, int englishScore) {
        String lang = target.languageOnly();
        if (englishScore == 0) {
            if (("ru".equals(lang) || "uk".equals(lang) || "bg".equals(lang) || "be".equals(lang)
                    || "sr".equals(lang) || "mk".equals(lang) || "kk".equals(lang)) && CYRILLIC.matcher(text).find()) {
                return Decision.SKIP_ALREADY_TARGET;
            }
            if ("zh".equals(lang) && HAN.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
            if ("ja".equals(lang) && JA_SCRIPT.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
            if ("ko".equals(lang) && HANGUL.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
            if (("ar".equals(lang) || "fa".equals(lang) || "ur".equals(lang)) && ARABIC.matcher(text).find()) {
                return Decision.SKIP_ALREADY_TARGET;
            }
            if ("he".equals(lang) && HEBREW.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
            if ("el".equals(lang) && GREEK.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
            if ("th".equals(lang) && THAI.matcher(text).find()) return Decision.SKIP_ALREADY_TARGET;
        }
        Set<String> targetWords = null;
        Pattern targetAccents = null;
        Set<String> sharedWords = Compat.setOf();
        if ("fr".equals(lang)) {
            targetWords = FRENCH_WORDS;
            targetAccents = FRENCH_ACCENTS;
            sharedWords = Compat.setOf("a", "on");
        } else if ("de".equals(lang)) {
            targetWords = GERMAN_WORDS;
            targetAccents = GERMAN_ACCENTS;
            sharedWords = Compat.setOf("in", "an");
        } else if ("es".equals(lang)) {
            targetWords = SPANISH_WORDS;
            targetAccents = SPANISH_ACCENTS;
            sharedWords = Compat.setOf("a");
        } else if ("pt".equals(lang)) {
            targetWords = PORTUGUESE_WORDS;
            targetAccents = PORTUGUESE_ACCENTS;
            sharedWords = Compat.setOf("a", "as");
        } else if ("it".equals(lang)) {
            targetWords = ITALIAN_WORDS;
            targetAccents = ITALIAN_ACCENTS;
            sharedWords = Compat.setOf("a", "in");
        }
        if (targetWords != null && words != null) {
            int targetScore = 0;
            int effectiveEnglish = englishScore;
            for (String word : words) {
                String lower = word.toLowerCase(Locale.ROOT);
                if (sharedWords.contains(lower) && ENGLISH_WORDS.contains(lower)) {
                    effectiveEnglish = Math.max(0, effectiveEnglish - 2);
                }
                String folded = fold(lower);
                if (targetWords.contains(folded)) targetScore += 2;
                if (targetAccents != null && targetAccents.matcher(word).find()) targetScore += 2;
            }
            if (targetScore >= 4 && targetScore > 2 * effectiveEnglish) return Decision.SKIP_ALREADY_TARGET;
        }
        if (extraTargetFeature != null && extraTargetFeature.matcher(text).find() && englishScore == 0) {
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
