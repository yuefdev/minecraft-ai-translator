package me.langyue.autotranslation.core.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small replacements for APIs that only exist from Java 9/11 on. The translation engine is compiled for Java 8
 * (Minecraft 1.12.2 runs on Java 8) and Gson 2.2.4 (the version 1.12.2 ships), so everything it needs from the
 * newer platform lives here.
 */
public final class Compat {
    private Compat() {
    }

    public static boolean isBlank(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    /** Like Java 11's {@code String.strip()}: removes leading and trailing whitespace. */
    public static String strip(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.charAt(start))) start++;
        while (end > start && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<T>(Arrays.asList(items)));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        Set<T> set = new LinkedHashSet<T>();
        for (T item : items) {
            if (!set.add(item)) throw new IllegalArgumentException("duplicate element: " + item);
        }
        return Collections.unmodifiableSet(set);
    }

    /** Alternating keys and values, like {@code Map.of}. */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) throw new IllegalArgumentException("odd number of arguments");
        Map<K, V> map = new LinkedHashMap<K, V>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((K) keysAndValues[i], (V) keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public static <T> List<T> listCopyOf(Collection<? extends T> items) {
        return Collections.unmodifiableList(new ArrayList<T>(items));
    }

    public static <T> Set<T> setCopyOf(Collection<? extends T> items) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(items));
    }

    public static String readString(Path file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file), charset);
    }

    public static String readString(Path file) throws IOException {
        return readString(file, StandardCharsets.UTF_8);
    }

    public static void writeString(Path file, CharSequence content, Charset charset, OpenOption... options) throws IOException {
        Files.write(file, content.toString().getBytes(charset), options);
    }

    public static void writeString(Path file, CharSequence content, OpenOption... options) throws IOException {
        writeString(file, content, StandardCharsets.UTF_8, options);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** {@code JsonParser.parseString} only exists from Gson 2.8.6; this works with every Gson version. */
    @SuppressWarnings("deprecation")
    public static JsonElement parseJson(String json) {
        return new JsonParser().parse(json);
    }
}
