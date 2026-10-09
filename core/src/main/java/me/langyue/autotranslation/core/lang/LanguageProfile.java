package me.langyue.autotranslation.core.lang;

import me.langyue.autotranslation.core.util.Compat;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a Minecraft language code (for example {@code tr_tr}) to the code and names used when talking to a
 * translation provider (for example {@code tr}, "Turkish").
 */
public final class LanguageProfile {
    private final String minecraftCode;
    private final String providerCode;
    private final String englishName;
    private final String nativeName;

    public LanguageProfile(String minecraftCode, String providerCode, String englishName, String nativeName) {
        this.minecraftCode = minecraftCode;
        this.providerCode = providerCode;
        this.englishName = englishName;
        this.nativeName = nativeName;
    }

    public String minecraftCode() {
        return minecraftCode;
    }

    public String providerCode() {
        return providerCode;
    }

    public String englishName() {
        return englishName;
    }

    public String nativeName() {
        return nativeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LanguageProfile other = (LanguageProfile) o;
        return java.util.Objects.equals(minecraftCode, other.minecraftCode) && java.util.Objects.equals(providerCode, other.providerCode) && java.util.Objects.equals(englishName, other.englishName) && java.util.Objects.equals(nativeName, other.nativeName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(minecraftCode, providerCode, englishName, nativeName);
    }

    @Override
    public String toString() {
        return "LanguageProfile[" + "minecraftCode=" + minecraftCode + ", " + "providerCode=" + providerCode + ", " + "englishName=" + englishName + ", " + "nativeName=" + nativeName + "]";
    }


    public static final LanguageProfile TURKISH = new LanguageProfile("tr_tr", "tr", "Turkish", "Türkçe");

    /** Regions whose script/variant matters to providers, so they keep the region subtag. */
    private static final Map<String, String> KEEP_REGION = Compat.mapOf(
            "zh_cn", "zh-CN",
            "zh_tw", "zh-TW",
            "zh_hk", "zh-HK",
            "pt_br", "pt-BR",
            "pt_pt", "pt-PT",
            "en_gb", "en-GB"
    );

    public static LanguageProfile of(String minecraftCode) {
        if (minecraftCode == null || Compat.isBlank(minecraftCode)) {
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
        if (Compat.isBlank(english)) english = language;
        if (Compat.isBlank(nativeName)) nativeName = english;
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
