package me.langyue.autotranslation.legacy;

import me.langyue.autotranslation.engine.LanguageSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The language files of every mod (and of resource packs), read the way the game reads them: all {@code en_us.lang}
 * files of all domains in load order, and the same for the target language. What is left is the text that would show
 * up in English. The text is cleaned up exactly like {@code Locale.loadLocaleData} does, because the translation is
 * looked up by that text.
 */
final class LanguageFiles implements LanguageSource {
    /** The same pattern as the game's: {@code %d} and {@code %.2f} become {@code %s}. */
    private static final Pattern NUMBER_FORMAT = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");

    @Override
    public Map<String, String> untranslated(String targetLanguage) throws IOException {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager == null) throw new IOException("the resources are not loaded yet");
        Map<String, String> english = new LinkedHashMap<String, String>();
        Map<String, String> target = new LinkedHashMap<String, String>();
        try {
            for (String domain : new ArrayList<String>(manager.getResourceDomains())) {
                read(manager, domain, "en_us", english);
                if (!"en_us".equals(targetLanguage)) read(manager, domain, targetLanguage, target);
            }
        } catch (RuntimeException e) {
            // the resources were reloaded while they were read (F3+T, resource pack change): try again later
            throw new IOException("the resources changed while they were read: " + e);
        }

        List<String> first = new ArrayList<String>();
        List<String> rest = new ArrayList<String>();
        for (Map.Entry<String, String> entry : english.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (target.containsKey(key) || value.length() < 2 || value.equals(key)) continue;
            (isName(key) ? first : rest).add(key);
        }
        Map<String, String> ordered = new LinkedHashMap<String, String>();
        for (String key : first) ordered.put(key, english.get(key));
        for (String key : rest) ordered.put(key, english.get(key));
        return ordered;
    }

    /** Names and menu text are what a player meets first; descriptions and the like come after them. */
    private static boolean isName(String key) {
        return key.endsWith(".name") || key.startsWith("itemGroup.") || key.startsWith("gui.") || key.startsWith("container.");
    }

    private static void read(IResourceManager manager, String domain, String language, Map<String, String> into) throws IOException {
        List<IResource> files;
        try {
            files = manager.getAllResources(new ResourceLocation(domain, "lang/" + language + ".lang"));
        } catch (FileNotFoundException e) {
            return; // this domain has no such language file
        }
        for (IResource file : files) {
            InputStream stream = file.getInputStream();
            try {
                BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String line;
                while ((line = lines.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    int equals = line.indexOf('=');
                    if (equals < 0) continue;
                    into.put(line.substring(0, equals), NUMBER_FORMAT.matcher(line.substring(equals + 1)).replaceAll("%$1s"));
                }
            } finally {
                stream.close();
            }
        }
    }
}
