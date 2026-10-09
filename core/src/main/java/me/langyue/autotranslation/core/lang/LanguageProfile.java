package me.langyue.autotranslation.core.lang;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a Minecraft language code (for example {@code tr_tr}) to the code and names used when talking to a
 * translation provider (for example {@code tr}, "Turkish").
 */
public record LanguageProfile(String minecraftCode, String providerCode, String englishName, String nativeName) {

    public static final LanguageProfile TURKISH = new LanguageProfile("tr_tr", "tr", "Turkish", "Türkçe");

    /** Regions whose script/variant matters to providers, so they keep the region subtag. */
    private static final Map<String, String> KEEP_REGION = Map.of(
            "zh_cn", "zh-CN",
            "zh_tw", "zh-TW",
            "zh_hk", "zh-HK",
            "pt_br", "pt-BR",
            "pt_pt", "pt-PT",
            "en_gb", "en-GB"
    );

    public static LanguageProfile of(String minecraftCode) {
        if (minecraftCode == null || minecraftCode.isBlank()) {
            return new LanguageProfile("en_us", "en", "English", "English");
        }
        String code = minecraftCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (code.equals("tr_tr") || code.equals("tr")) {
            return TURKISH;
        }
        String language = code.contains("_") ? code.substring(0, code.indexOf('_')) : code;
        String provider = KEEP_REGION.getOrDefault(code, language);
        Locale locale = Locale.forLanguageTag(language);
        String english = locale.getDisplayLanguage(Locale.ENGLISH);
        String nativeName = locale.getDisplayLanguage(locale);
        if (english.isBlank()) english = language;
        if (nativeName.isBlank()) nativeName = english;
        return new LanguageProfile(code, provider, english, nativeName);
    }

    public boolean isTurkish() {
        return providerCode.equals("tr");
    }

    /** Language subtag only, e.g. {@code tr} for {@code tr_tr}. */
    public String languageOnly() {
        int dash = providerCode.indexOf('-');
        return dash < 0 ? providerCode : providerCode.substring(0, dash);
    }
}
