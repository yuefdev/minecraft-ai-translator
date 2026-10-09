package me.langyue.autotranslation.core.glossary;

import me.langyue.autotranslation.core.util.Compat;
import me.langyue.autotranslation.core.util.Hashing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Editable terminology list so that the same item or mechanic always gets the same Turkish name.
 *
 * <p>There are two layers. <em>Terms</em> are the starter list plus the user's own edits; their content decides
 * the {@link #version()} that is part of every cache key, so editing a term makes older translations stale.
 * <em>Hints</em> are derived automatically from the language files of the installed game/mods (English value
 * to Turkish value); they are passed to the provider as extra context but do not change the version, otherwise
 * installing a resource pack would throw the whole cache away.
 */
public final class Glossary {

    public static final class Term {
        private final String source;
        private final String target;

        public Term(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String source() {
            return source;
        }

        public String target() {
            return target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Term other = (Term) o;
            return java.util.Objects.equals(source, other.source) && java.util.Objects.equals(target, other.target);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(source, target);
        }

        @Override
        public String toString() {
            return "Term[" + "source=" + source + ", " + "target=" + target + "]";
        }
    }

    private static final class Compiled {
        private final Term term;
        private final Pattern pattern;

        public Compiled(Term term, Pattern pattern) {
            this.term = term;
            this.pattern = pattern;
        }

        public Term term() {
            return term;
        }

        public Pattern pattern() {
            return pattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Compiled other = (Compiled) o;
            return java.util.Objects.equals(term, other.term) && java.util.Objects.equals(pattern, other.pattern);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(term, pattern);
        }

        @Override
        public String toString() {
            return "Compiled[" + "term=" + term + ", " + "pattern=" + pattern + "]";
        }
    }

    private final Object lock = new Object();
    private volatile Map<String, String> terms = Compat.mapOf();
    private volatile Map<String, String> hints = Compat.mapOf();
    private volatile List<Compiled> compiled = Compat.listOf();
    private volatile Map<String, Term> hintIndex = Compat.mapOf();
    private volatile String version = "0";

    public Glossary() {
        rebuild(new LinkedHashMap<>(), Compat.mapOf());
    }

    public static Glossary of(Map<String, String> terms) {
        Glossary g = new Glossary();
        g.replaceTerms(terms);
        return g;
    }

    public void replaceTerms(Map<String, String> newTerms) {
        synchronized (lock) {
            rebuild(clean(newTerms), hints);
        }
    }

    public void replaceHints(Map<String, String> newHints) {
        synchronized (lock) {
            rebuild(new LinkedHashMap<>(terms), clean(newHints));
        }
    }

    public void put(String source, String target) {
        if (source == null || Compat.isBlank(source) || target == null || Compat.isBlank(target)) return;
        synchronized (lock) {
            Map<String, String> copy = new LinkedHashMap<>(terms);
            copy.remove(findKey(copy, source));
            copy.put(Compat.strip(source), Compat.strip(target));
            rebuild(copy, hints);
        }
    }

    public void remove(String source) {
        if (source == null) return;
        synchronized (lock) {
            Map<String, String> copy = new LinkedHashMap<>(terms);
            String key = findKey(copy, source);
            if (key != null) {
                copy.remove(key);
                rebuild(copy, hints);
            }
        }
    }

    public Map<String, String> terms() {
        return terms;
    }

    /** Content hash of the editable terms; part of every cache key. */
    public String version() {
        return version;
    }

    public int hintCount() {
        return hints.size();
    }

    /**
     * Terms occurring in {@code text} as whole words. User terms win over hints; longer terms are listed first.
     */
    public Map<String, String> matching(String text, int limit) {
        Map<String, String> found = new LinkedHashMap<>();
        if (text == null || Compat.isBlank(text) || limit <= 0) return found;
        for (Compiled c : compiled) {
            if (found.size() >= limit) break;
            if (c.pattern().matcher(text).find()) {
                found.put(c.term().source(), c.term().target());
            }
        }
        Map<String, Term> hintsNow = hintIndex;
        if (found.size() < limit && !hintsNow.isEmpty()) {
            List<String> words = new ArrayList<>();
            java.util.regex.Matcher m = WORD.matcher(text);
            while (m.find()) words.add(m.group().toLowerCase(Locale.ROOT));
            for (int n = Math.min(4, words.size()); n >= 1 && found.size() < limit; n--) {
                for (int i = 0; i + n <= words.size() && found.size() < limit; i++) {
                    Term term = hintsNow.get(String.join(" ", words.subList(i, i + n)));
                    if (term != null) found.putIfAbsent(term.source(), term.target());
                }
            }
        }
        return found;
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*");

    private static String normalizeKey(String key) {
        return String.join(" ", Compat.strip(key.toLowerCase(Locale.ROOT)).split("\\s+"));
    }

    private void rebuild(Map<String, String> newTerms, Map<String, String> newHints) {
        // Hints can number in the tens of thousands, so they are matched by word n-grams in a hash map instead of
        // one regular expression each. Only the (few) editable terms are compiled to patterns.
        Map<String, Term> hintMap = new java.util.HashMap<>();
        for (Map.Entry<String, String> hint : newHints.entrySet()) {
            if (findKey(newTerms, hint.getKey()) == null) {
                hintMap.put(normalizeKey(hint.getKey()), new Term(hint.getKey(), hint.getValue()));
            }
        }
        this.hintIndex = hintMap;
        Map<String, String> merged = new LinkedHashMap<>(newTerms);
        List<Compiled> list = new ArrayList<>(merged.size());
        for (Map.Entry<String, String> e : merged.entrySet()) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(e.getKey()) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            list.add(new Compiled(new Term(e.getKey(), e.getValue()), p));
        }
        list.sort(Comparator.comparingInt((Compiled c) -> c.term().source().length()).reversed());
        StringBuilder canonical = new StringBuilder();
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(newTerms);
        sorted.forEach((k, v) -> canonical.append(k.toLowerCase(Locale.ROOT)).append('\u0000').append(v).append('\n'));
        this.terms = Collections.unmodifiableMap(new LinkedHashMap<>(newTerms));
        this.hints = Collections.unmodifiableMap(new LinkedHashMap<>(newHints));
        this.compiled = Compat.listCopyOf(list);
        this.version = Hashing.sha256(canonical.toString()).substring(0, 12);
    }

    private static Map<String, String> clean(Map<String, String> input) {
        Map<String, String> out = new LinkedHashMap<>();
        if (input == null) return out;
        input.forEach((k, v) -> {
            if (k != null && v != null && !Compat.isBlank(k) && !Compat.isBlank(v)) out.put(Compat.strip(k), Compat.strip(v));
        });
        return out;
    }

    private static String findKey(Map<String, String> map, String key) {
        if (key == null) return null;
        for (String existing : map.keySet()) {
            if (existing.equalsIgnoreCase(Compat.strip(key))) return existing;
        }
        return null;
    }
}
